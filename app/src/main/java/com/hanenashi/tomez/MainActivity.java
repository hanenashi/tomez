package com.hanenashi.tomez;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.util.AtomicFile;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.ContextThemeWrapper;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.view.inputmethod.InputMethodManager;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int OPEN_DOCUMENT = 1;
    private static final int CREATE_DOCUMENT = 2;
    private static final int MAX_OPEN_BYTES = 1024 * 1024;
    private static final int DRAFT_VERSION = 2;
    private static final long DRAFT_DELAY_MS = 1000;

    private static final int FONT_SANS = 10;
    private static final int FONT_SERIF = 11;
    private static final int FONT_MONO = 12;
    private static final int THEME_LIGHT = 30;
    private static final int THEME_DARK = 31;
    private static final int OLD_THEME_BLACK = 32;
    private static final int THEME_GREY = 33;
    private static final int THEME_GREEN = 34;
    private static final int MIN_SLIDER_SIZE = 10;
    private static final int MAX_SLIDER_SIZE = 40;
    private static final int MIN_CUSTOM_SIZE = 8;
    private static final int MAX_CUSTOM_SIZE = 96;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private EditText editor;
    private ScrollView documentScroll;
    private TextView title;
    private ImageButton pencilButton;
    private ImageButton wrapButton;
    private ImageButton menuButton;
    private View toolbar;
    private View divider;
    private LinearLayout root;
    private SharedPreferences preferences;
    private AtomicFile draftFile;
    private Uri documentUri;
    private String documentName = "Untitled";
    private String documentPath = "Untitled";
    private String savedText = "";
    private boolean hasBom;
    private boolean dirty;
    private boolean suppressChanges;
    private boolean busy;
    private boolean keyboardRequestPending;
    private int menuSurface;
    private int menuForeground;
    private int menuMuted;
    private int menuLine;
    private int menuAccent;
    private Runnable afterSave;
    private final Runnable showKeyboardRequest = () -> {
        keyboardRequestPending = false;
        try {
            if (editor.hasFocus() && editor.hasWindowFocus())
                ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                        .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
        } finally {
            // Keep later taps and scrolls quiet after the keyboard is dismissed.
            editor.setShowSoftInputOnFocus(false);
        }
    };
    private final Runnable updateDraft = () -> {
        if (dirty) saveDraft();
        else clearDraft();
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("appearance", MODE_PRIVATE);
        if (preferences.getInt("theme", THEME_LIGHT) == OLD_THEME_BLACK)
            preferences.edit().putInt("theme", THEME_DARK).apply();
        if (preferences.contains("cursor")) preferences.edit().remove("cursor").apply();
        draftFile = new AtomicFile(new File(getFilesDir(), "unsaved-draft"));
        buildUi();
        restoreDraft();
        applyAppearance();
        updateTitle();
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        root.requestApplyInsets();
        applySystemUi();
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(),
                        insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(),
                        insets.getSystemWindowInsetBottom());
            }
            return insets;
        });

        LinearLayout toolbarRow = new LinearLayout(this);
        toolbarRow.setGravity(Gravity.CENTER_VERTICAL);
        toolbarRow.setPadding(dp(12), dp(4), dp(8), dp(4));
        toolbar = toolbarRow;

        title = new TextView(this);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setOnClickListener(view -> showFileLocation());
        title.setOnLongClickListener(view -> {
            showFileLocation();
            return true;
        });
        toolbarRow.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));

        pencilButton = new ImageButton(this);
        pencilButton.setImageResource(R.drawable.ic_edit);
        pencilButton.setPadding(dp(12), dp(12), dp(12), dp(12));
        pencilButton.setBackgroundColor(Color.TRANSPARENT);
        pencilButton.setContentDescription("Show or hide keyboard");
        pencilButton.setOnClickListener(view -> toggleKeyboard());
        toolbarRow.addView(pencilButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        wrapButton = new ImageButton(this);
        wrapButton.setImageResource(R.drawable.ic_wrap);
        wrapButton.setPadding(dp(12), dp(12), dp(12), dp(12));
        wrapButton.setBackgroundColor(Color.TRANSPARENT);
        wrapButton.setOnClickListener(view -> toggleLineWrapping());
        toolbarRow.addView(wrapButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        menuButton = new ImageButton(this);
        menuButton.setImageResource(R.drawable.ic_more_vert);
        menuButton.setPadding(dp(12), dp(12), dp(12), dp(12));
        menuButton.setBackgroundColor(Color.TRANSPARENT);
        menuButton.setContentDescription("Menu and settings");
        menuButton.setOnClickListener(view -> showMenu());
        toolbarRow.addView(menuButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(toolbarRow);

        divider = new View(this);
        root.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        documentScroll = new ScrollView(this) {
            @Override
            public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
                // A focused EditText can claim a diagonal swipe for cursor dragging.
                // Wrapped text has no horizontal scrolling: let ScrollView keep
                // detecting vertical drags, but respect drag-to-select gestures.
                if (disallowIntercept && editor != null && !editor.hasSelection()
                        && preferences.getBoolean("wrap_lines", true)) return;
                super.requestDisallowInterceptTouchEvent(disallowIntercept);
            }
        };
        documentScroll.setFillViewport(true);
        documentScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        documentScroll.setVerticalScrollBarEnabled(true);
        documentScroll.setVerticalScrollbarPosition(View.SCROLLBAR_POSITION_RIGHT);
        documentScroll.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        documentScroll.setScrollBarSize(dp(3));
        documentScroll.setScrollbarFadingEnabled(true);
        documentScroll.setScrollBarDefaultDelayBeforeFade(600);
        documentScroll.setScrollBarFadeDuration(350);

        editor = new EditText(this);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setPadding(dp(16), dp(12), dp(16), dp(16));
        editor.setBackgroundColor(Color.TRANSPARENT);
        editor.setSingleLine(false);
        editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editor.setHorizontallyScrolling(!preferences.getBoolean("wrap_lines", true));
        editor.setVerticalScrollBarEnabled(false);
        editor.setShowSoftInputOnFocus(false);
        editor.setHint("Start typing…");
        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable text) {
                if (suppressChanges) return;
                dirty = !savedText.contentEquals(text);
                updateTitle();
                mainHandler.removeCallbacks(updateDraft);
                mainHandler.postDelayed(updateDraft, DRAFT_DELAY_MS);
            }
        });
        documentScroll.addView(editor, new ScrollView.LayoutParams(-1, -2));
        root.addView(documentScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void showFileLocation() {
        hideKeyboard();
        Context context = themedContext();
        LinearLayout panel = menuPanel(context);
        AlertDialog dialog = new AlertDialog.Builder(context).setView(panel).create();
        menuHeading(panel, "File location");

        TextView path = new TextView(context);
        path.setText(documentPath);
        path.setTextSize(14);
        path.setTextColor(menuForeground);
        path.setTextIsSelectable(true);
        path.setPadding(dp(8), dp(12), dp(8), dp(16));
        panel.addView(path);

        menuDivider(panel);
        TextView done = new TextView(context);
        done.setText("Done");
        done.setTextSize(14);
        done.setTextColor(menuAccent);
        done.setGravity(Gravity.CENTER);
        done.setOnClickListener(view -> dialog.dismiss());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(dp(72), dp(48));
        doneParams.gravity = Gravity.END;
        panel.addView(done, doneParams);
        showPanelDialog(dialog);
    }

    private void showMenu() {
        hideKeyboard();
        Context context = themedContext();
        LinearLayout panel = menuPanel(context);
        AlertDialog dialog = new AlertDialog.Builder(context).setView(panel).create();

        LinearLayout files = new LinearLayout(context);
        files.setGravity(Gravity.CENTER);
        panel.addView(files);
        fileAction(files, "New", dialog, () -> confirmDiscard(this::newDocument));
        fileAction(files, "Open", dialog, () -> confirmDiscard(this::launchOpen));
        fileAction(files, "Save", dialog, () -> save(null));
        fileAction(files, "Save As", dialog, this::launchCreate);
        fileAction(files, "Close", dialog, () -> confirmDiscard(this::closeDocument));

        menuDivider(panel);
        int font = preferences.getInt("font", FONT_SANS);
        String fontName = font == FONT_SERIF ? "System serif"
                : font == FONT_MONO ? "Monospace" : "System sans";
        settingRow(panel, "Font", fontName, dialog, this::showFontChooser);
        settingRow(panel, "Text size", preferences.getInt("size", 18) + " sp",
                dialog, this::showTextSizeDialog);
        int theme = preferences.getInt("theme", THEME_LIGHT);
        String themeName = theme == THEME_DARK ? "Dark" : theme == THEME_GREY ? "Grey"
                : theme == THEME_GREEN ? "Green / Matrix" : "Light";
        settingRow(panel, "Theme", themeName, dialog, this::showThemeChooser);

        LinearLayout fullscreenRow = new LinearLayout(context);
        fullscreenRow.setGravity(Gravity.CENTER_VERTICAL);
        fullscreenRow.setPadding(dp(8), 0, dp(8), 0);
        panel.addView(fullscreenRow, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView fullscreenLabel = new TextView(context);
        fullscreenLabel.setText("Fullscreen");
        fullscreenLabel.setTextSize(15);
        fullscreenLabel.setTextColor(menuForeground);
        fullscreenRow.addView(fullscreenLabel, new LinearLayout.LayoutParams(0, -2, 1));
        Switch fullscreen = new Switch(context);
        fullscreen.setChecked(preferences.getBoolean("fullscreen", false));
        fullscreen.setContentDescription("Fullscreen");
        fullscreen.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean("fullscreen", checked).apply();
            applySystemUi();
        });
        fullscreenRow.addView(fullscreen);
        fullscreenRow.setOnClickListener(view -> fullscreen.setChecked(!fullscreen.isChecked()));

        menuDivider(panel);
        LinearLayout footer = new LinearLayout(context);
        footer.setGravity(Gravity.CENTER);
        panel.addView(footer, new LinearLayout.LayoutParams(-1, dp(32)));
        TextView version = new TextView(context);
        version.setText("tomez " + BuildConfig.VERSION_NAME + " · ");
        version.setTextSize(12);
        version.setTextColor(menuMuted);
        footer.addView(version);
        TextView github = new TextView(context);
        github.setText("GitHub");
        github.setTextSize(12);
        github.setTextColor(menuAccent);
        github.setOnClickListener(view -> {
            dialog.dismiss();
            openGithub();
        });
        footer.addView(github, new LinearLayout.LayoutParams(-2, dp(32)));
        github.setGravity(Gravity.CENTER_VERTICAL);

        showPanelDialog(dialog);
    }

    private LinearLayout menuPanel(Context context) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(8), dp(12), dp(8));
        panel.setBackgroundColor(menuSurface);
        return panel;
    }

    private void showPanelDialog(AlertDialog dialog) {
        dialog.show();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(menuSurface));
        dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels
                - dp(32), dp(380)), -2);
    }

    private void menuHeading(LinearLayout panel, String label) {
        TextView heading = new TextView(panel.getContext());
        heading.setText(label);
        heading.setTextSize(16);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setTextColor(menuForeground);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPadding(dp(8), 0, dp(8), 0);
        panel.addView(heading, new LinearLayout.LayoutParams(-1, dp(44)));
        menuDivider(panel);
    }

    private void fileAction(LinearLayout row, String label, AlertDialog dialog, Runnable action) {
        TextView button = new TextView(row.getContext());
        button.setText(label);
        button.setTextSize(13);
        button.setTextColor(label.equals("Close") ? menuMuted : menuForeground);
        button.setGravity(Gravity.CENTER);
        button.setOnClickListener(view -> {
            dialog.dismiss();
            action.run();
        });
        row.addView(button, new LinearLayout.LayoutParams(0, dp(56), 1));
    }

    private void settingRow(LinearLayout panel, String label, String value,
                            AlertDialog dialog, Runnable action) {
        Context context = panel.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), 0, dp(8), 0);
        panel.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
        TextView name = new TextView(context);
        name.setText(label);
        name.setTextSize(15);
        name.setTextColor(menuForeground);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView selected = new TextView(context);
        selected.setText(value);
        selected.setTextSize(14);
        selected.setTextColor(menuMuted);
        row.addView(selected);
        row.setOnClickListener(view -> {
            dialog.dismiss();
            action.run();
        });
    }

    private void menuDivider(LinearLayout panel) {
        View line = new View(panel.getContext());
        line.setBackgroundColor(menuLine);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.setMargins(dp(8), dp(4), dp(8), dp(4));
        panel.addView(line, params);
    }

    private void showFontChooser() {
        showChoiceDialog("Font", "font", new String[]{"System sans", "System serif", "Monospace"},
                new int[]{FONT_SANS, FONT_SERIF, FONT_MONO}, FONT_SANS);
    }

    private void showThemeChooser() {
        showChoiceDialog("Theme", "theme", new String[]{"Light", "Dark", "Grey", "Green / Matrix"},
                new int[]{THEME_LIGHT, THEME_DARK, THEME_GREY, THEME_GREEN}, THEME_LIGHT);
    }

    private void showChoiceDialog(String title, String key, String[] labels,
                                  int[] values, int fallback) {
        Context context = themedContext();
        LinearLayout panel = menuPanel(context);
        AlertDialog dialog = new AlertDialog.Builder(context).setView(panel).create();
        menuHeading(panel, title);
        RadioGroup choices = new RadioGroup(context);
        panel.addView(choices);
        ColorStateList radioColors = new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{menuAccent, menuMuted});
        int selected = preferences.getInt(key, fallback);
        for (int i = 0; i < labels.length; i++) {
            final int value = values[i];
            RadioButton choice = new RadioButton(context);
            choice.setId(View.generateViewId());
            choice.setText(labels[i]);
            choice.setTextSize(15);
            choice.setTextColor(menuForeground);
            choice.setButtonTintList(radioColors);
            choice.setGravity(Gravity.CENTER_VERTICAL);
            choice.setPadding(dp(8), 0, dp(8), 0);
            choices.addView(choice, new RadioGroup.LayoutParams(-1, dp(48)));
            choice.setChecked(value == selected);
            choice.setOnClickListener(view -> {
                preferences.edit().putInt(key, value).apply();
                applyAppearance();
                dialog.dismiss();
            });
        }
        showPanelDialog(dialog);
    }

    private void showTextSizeDialog() {
        Context context = themedContext();
        LinearLayout content = menuPanel(context);
        menuHeading(content, "Text size");

        TextView help = new TextView(context);
        help.setText("Slide from 10–40 sp, or enter 8–96 sp.");
        help.setTextSize(13);
        help.setTextColor(menuMuted);
        help.setPadding(dp(8), dp(8), dp(8), 0);
        content.addView(help);

        SeekBar slider = new SeekBar(context);
        slider.setMax(MAX_SLIDER_SIZE - MIN_SLIDER_SIZE);
        slider.setThumbTintList(ColorStateList.valueOf(menuAccent));
        slider.setProgressTintList(ColorStateList.valueOf(menuAccent));
        slider.setProgressBackgroundTintList(ColorStateList.valueOf(menuLine));
        int currentSize = preferences.getInt("size", 18);
        slider.setProgress(Math.max(0, Math.min(MAX_SLIDER_SIZE - MIN_SLIDER_SIZE,
                currentSize - MIN_SLIDER_SIZE)));
        content.addView(slider, new LinearLayout.LayoutParams(-1, dp(52)));

        EditText manual = new EditText(context);
        manual.setInputType(InputType.TYPE_CLASS_NUMBER);
        manual.setSingleLine(true);
        manual.setSelectAllOnFocus(true);
        manual.setHint("Size in sp");
        manual.setText(String.valueOf(currentSize));
        manual.setTextColor(menuForeground);
        manual.setHintTextColor(menuMuted);
        manual.setBackgroundTintList(ColorStateList.valueOf(menuAccent));
        LinearLayout.LayoutParams manualParams = new LinearLayout.LayoutParams(-1, -2);
        manualParams.setMargins(dp(8), 0, dp(8), dp(4));
        content.addView(manual, manualParams);

        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    manual.setText(String.valueOf(MIN_SLIDER_SIZE + progress));
                    manual.setSelection(manual.length());
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        manual.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable text) {
                try {
                    int size = Integer.parseInt(text.toString());
                    slider.setProgress(Math.max(0, Math.min(MAX_SLIDER_SIZE - MIN_SLIDER_SIZE,
                            size - MIN_SLIDER_SIZE)));
                    if (size >= MIN_CUSTOM_SIZE && size <= MAX_CUSTOM_SIZE)
                        editor.setTextSize(size);
                } catch (NumberFormatException ignored) { }
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(context).setView(content).create();
        dialog.setOnDismissListener(ignored -> editor.setTextSize(preferences.getInt("size", 18)));
        menuDivider(content);
        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.END);
        content.addView(actions);
        TextView cancel = new TextView(context);
        cancel.setText("Cancel");
        cancel.setTextSize(14);
        cancel.setTextColor(menuMuted);
        cancel.setGravity(Gravity.CENTER);
        cancel.setOnClickListener(view -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(dp(72), dp(48)));
        TextView apply = new TextView(context);
        apply.setText("Apply");
        apply.setTextSize(14);
        apply.setTextColor(menuAccent);
        apply.setGravity(Gravity.CENTER);
        apply.setOnClickListener(view -> {
            int size;
            try {
                size = Integer.parseInt(manual.getText().toString());
            } catch (NumberFormatException error) {
                manual.setError("Enter a number from 8 to 96.");
                return;
            }
            if (size < MIN_CUSTOM_SIZE || size > MAX_CUSTOM_SIZE) {
                manual.setError("Enter a number from 8 to 96.");
                return;
            }
            preferences.edit().putInt("size", size).apply();
            dialog.dismiss();
        });
        actions.addView(apply, new LinearLayout.LayoutParams(dp(72), dp(48)));
        dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        showPanelDialog(dialog);
        dialog.getWindow().setDimAmount(0.2f);
    }

    private void openGithub() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/hanenashi/tomez")));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "No browser available.", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyAppearance() {
        int theme = preferences.getInt("theme", THEME_LIGHT);
        int background;
        int surface;
        int foreground;
        int muted;
        int line;
        int accent;
        switch (theme) {
            case THEME_DARK:
                background = Color.rgb(28, 30, 34);
                surface = Color.rgb(38, 40, 44);
                foreground = Color.rgb(239, 239, 239);
                muted = Color.rgb(165, 165, 165);
                line = Color.rgb(70, 70, 70);
                accent = Color.rgb(151, 197, 195);
                break;
            case THEME_GREY:
                background = Color.rgb(218, 221, 219);
                surface = Color.rgb(195, 200, 197);
                foreground = Color.rgb(30, 37, 35);
                muted = Color.rgb(84, 95, 90);
                line = Color.rgb(164, 172, 167);
                accent = Color.rgb(33, 93, 70);
                break;
            case THEME_GREEN:
                background = Color.rgb(5, 18, 8);
                surface = Color.rgb(9, 31, 13);
                foreground = Color.rgb(103, 255, 119);
                muted = Color.rgb(52, 166, 69);
                line = Color.rgb(31, 94, 43);
                accent = Color.rgb(86, 255, 113);
                break;
            default:
                background = Color.WHITE;
                surface = Color.rgb(247, 247, 247);
                foreground = Color.rgb(28, 28, 28);
                muted = Color.rgb(110, 110, 110);
                line = Color.rgb(225, 225, 225);
                accent = Color.rgb(38, 102, 145);
                break;
        }
        root.setBackgroundColor(background);
        menuSurface = surface;
        menuForeground = foreground;
        menuMuted = muted;
        menuLine = line;
        menuAccent = accent;
        toolbar.setBackgroundColor(surface);
        divider.setBackgroundColor(line);
        title.setTextColor(foreground);
        pencilButton.setImageTintList(ColorStateList.valueOf(foreground));
        updateWrapButton();
        menuButton.setImageTintList(ColorStateList.valueOf(foreground));
        editor.setTextColor(foreground);
        editor.setHintTextColor(muted);
        editor.setHighlightColor(Color.argb(90, Color.red(accent), Color.green(accent), Color.blue(accent)));
        if (Build.VERSION.SDK_INT >= 29) {
            documentScroll.setEdgeEffectColor(accent);
            GradientDrawable scrollThumb = new GradientDrawable();
            scrollThumb.setColor(Color.argb(180, Color.red(muted), Color.green(muted), Color.blue(muted)));
            scrollThumb.setCornerRadius(dp(3));
            documentScroll.setVerticalScrollbarThumbDrawable(scrollThumb);
            documentScroll.setVerticalScrollbarTrackDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        int font = preferences.getInt("font", FONT_SANS);
        Typeface typeface = font == FONT_SERIF ? Typeface.SERIF
                : font == FONT_MONO ? Typeface.MONOSPACE : Typeface.SANS_SERIF;
        editor.setTypeface(typeface);
        int size = preferences.getInt("size", 18);
        editor.setTextSize(size);
        applySystemUi();
    }

    private void applySystemUi() {
        int theme = preferences.getInt("theme", THEME_LIGHT);
        boolean lightBars = theme == THEME_LIGHT || theme == THEME_GREY;
        boolean fullscreen = preferences.getBoolean("fullscreen", false);
        int surface = theme == THEME_GREEN ? Color.rgb(9, 31, 13)
                : theme == THEME_DARK ? Color.rgb(38, 40, 44)
                : theme == THEME_GREY ? Color.rgb(195, 200, 197) : Color.rgb(247, 247, 247);
        int background = theme == THEME_GREEN ? Color.rgb(5, 18, 8)
                : theme == THEME_DARK ? Color.rgb(28, 30, 34)
                : theme == THEME_GREY ? Color.rgb(218, 221, 219) : Color.WHITE;
        getWindow().setStatusBarColor(surface);
        getWindow().setNavigationBarColor(background);
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller == null) return;
            int lightAppearance = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            controller.setSystemBarsAppearance(lightBars ? lightAppearance : 0, lightAppearance);
            if (fullscreen) {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.systemBars());
            } else {
                controller.show(WindowInsets.Type.systemBars());
            }
        } else {
            int flags = lightBars ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0;
            if (fullscreen) flags |= View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && preferences != null) applySystemUi();
    }

    private Context themedContext() {
        int theme = preferences.getInt("theme", THEME_LIGHT);
        return new ContextThemeWrapper(this, theme == THEME_LIGHT || theme == THEME_GREY
                ? android.R.style.Theme_Material_Light_NoActionBar
                : android.R.style.Theme_Material_NoActionBar);
    }

    private void confirmDiscard(Runnable action) {
        if (!dirty) {
            action.run();
            return;
        }
        new AlertDialog.Builder(themedContext())
                .setTitle("Unsaved changes")
                .setMessage("Save changes to " + documentName + "?")
                .setPositiveButton("Save", (dialog, which) -> save(action))
                .setNeutralButton("Discard", (dialog, which) -> {
                    setEditorText(savedText);
                    clearDraft();
                    action.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void newDocument() {
        clearDocument();
        showKeyboard();
    }

    private void closeDocument() {
        hideKeyboard();
        clearDocument();
        root.setFocusableInTouchMode(true);
        root.requestFocus();
    }

    private void clearDocument() {
        documentUri = null;
        documentName = "Untitled";
        documentPath = "Untitled";
        hasBom = false;
        setEditorText("");
        clearDraft();
    }

    private void launchOpen() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, OPEN_DOCUMENT);
    }

    private void launchCreate() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("text/plain");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE,
                documentUri == null ? "Untitled.txt" : documentName);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, CREATE_DOCUMENT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != OPEN_DOCUMENT && requestCode != CREATE_DOCUMENT) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (requestCode == CREATE_DOCUMENT) afterSave = null;
            return;
        }
        Uri uri = data.getData();
        persistPermission(uri, data.getFlags());
        if (requestCode == OPEN_DOCUMENT) open(uri);
        else write(uri);
    }

    private void persistPermission(Uri uri, int flags) {
        int granted = flags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (granted == 0) return;
        try {
            getContentResolver().takePersistableUriPermission(uri, granted);
        } catch (SecurityException ignored) {
            // The URI is still usable for this session if the provider did not offer persistence.
        }
    }

    private void open(Uri uri) {
        setBusy(true);
        io.execute(() -> {
            try {
                OpenedDocument opened = readDocument(uri);
                runOnUiThread(() -> {
                    setBusy(false);
                    documentUri = uri;
                    documentName = queryName(uri, "Untitled");
                    documentPath = queryPath(uri, documentName);
                    hasBom = opened.hasBom;
                    setEditorText(opened.text);
                    clearDraft();
                    hideKeyboard();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    setBusy(false);
                    showError("Could not open document", error);
                });
            }
        });
    }

    private OpenedDocument readDocument(Uri uri) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("The document provider did not return a file.");
            byte[] chunk = new byte[8192];
            int count;
            while ((count = input.read(chunk)) != -1) {
                if (bytes.size() + count > MAX_OPEN_BYTES)
                    throw new IOException("Files larger than 1 MiB are not supported yet.");
                bytes.write(chunk, 0, count);
            }
        }
        byte[] raw = bytes.toByteArray();
        boolean bom = raw.length >= 3 && (raw[0] & 255) == 0xef
                && (raw[1] & 255) == 0xbb && (raw[2] & 255) == 0xbf;
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw, bom ? 3 : 0, raw.length - (bom ? 3 : 0)))
                    .toString();
            return new OpenedDocument(text, bom);
        } catch (CharacterCodingException error) {
            throw new IOException("This file is not valid UTF-8.", error);
        }
    }

    private void save(Runnable actionAfterSave) {
        afterSave = actionAfterSave;
        if (documentUri == null) launchCreate();
        else write(documentUri);
    }

    private void write(Uri uri) {
        String text = editor.getText().toString();
        boolean bom = hasBom;
        setBusy(true);
        io.execute(() -> {
            try {
                ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .encode(CharBuffer.wrap(text));
                byte[] bytes = new byte[encoded.remaining()];
                encoded.get(bytes);
                try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) throw new IOException("The document provider did not accept the file.");
                    if (bom) output.write(new byte[]{(byte) 0xef, (byte) 0xbb, (byte) 0xbf});
                    output.write(bytes);
                    output.flush();
                }
                // Closing the provider stream is part of a successful save.
                // Do not mark the document clean until it finishes.
            } catch (Exception error) {
                Exception visibleError = error instanceof CharacterCodingException
                        ? new IOException("Text contains invalid Unicode.", error) : error;
                runOnUiThread(() -> {
                    setBusy(false);
                    afterSave = null;
                    showError("Could not save document", visibleError);
                });
                return;
            }
            runOnUiThread(() -> {
                setBusy(false);
                documentUri = uri;
                documentName = queryName(uri, "Untitled.txt");
                documentPath = queryPath(uri, documentName);
                savedText = text;
                dirty = !savedText.contentEquals(editor.getText());
                updateTitle();
                mainHandler.removeCallbacks(updateDraft);
                clearDraft();
                Runnable next = afterSave;
                afterSave = null;
                if (next != null) next.run();
            });
        });
    }

    private String queryName(Uri uri, String fallback) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0))
                return cursor.getString(0);
        } catch (Exception ignored) { }
        return fallback;
    }

    private String queryPath(Uri uri, String name) {
        if ("com.android.externalstorage.documents".equals(uri.getAuthority())) {
            try {
                String id = DocumentsContract.getDocumentId(uri);
                int colon = id.indexOf(':');
                if (colon > 0 && colon < id.length() - 1) {
                    String volume = id.substring(0, colon);
                    String relative = id.substring(colon + 1);
                    if ("primary".equalsIgnoreCase(volume)) return relative;
                    if ("home".equalsIgnoreCase(volume))
                        return relative.startsWith("Documents/") ? relative : "Documents/" + relative;
                    return volume + "/" + relative;
                }
            } catch (IllegalArgumentException ignored) { }
        }
        if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {
            try {
                String id = DocumentsContract.getDocumentId(uri);
                if (id.startsWith("raw:")) return userVisiblePath(id.substring(4));
            } catch (IllegalArgumentException ignored) { }
            return "Download/" + name;
        }
        if ("file".equals(uri.getScheme()) && uri.getPath() != null)
            return userVisiblePath(uri.getPath());
        // Other document providers can use opaque IDs rather than filesystem paths.
        return name + " · " + uri;
    }

    private String userVisiblePath(String path) {
        for (String prefix : new String[]{"/storage/emulated/0/", "/storage/self/primary/", "/sdcard/"}) {
            if (path.startsWith(prefix)) return path.substring(prefix.length());
        }
        return path;
    }

    private void setEditorText(String text) {
        suppressChanges = true;
        editor.setText(text);
        editor.setSelection(0);
        documentScroll.scrollTo(0, 0);
        suppressChanges = false;
        savedText = text;
        dirty = false;
        updateTitle();
    }

    private void setBusy(boolean value) {
        busy = value;
        editor.setEnabled(!value);
        pencilButton.setEnabled(!value);
        wrapButton.setEnabled(!value);
        menuButton.setEnabled(!value);
    }

    private void toggleLineWrapping() {
        boolean wrap = !preferences.getBoolean("wrap_lines", true);
        preferences.edit().putBoolean("wrap_lines", wrap).apply();
        editor.setHorizontallyScrolling(!wrap);
        if (wrap) editor.scrollTo(0, editor.getScrollY());
        updateWrapButton();
    }

    private void updateWrapButton() {
        boolean wrap = preferences.getBoolean("wrap_lines", true);
        wrapButton.setImageTintList(ColorStateList.valueOf(wrap ? menuForeground : menuMuted));
        wrapButton.setContentDescription(wrap
                ? "Line wrapping on. Tap to turn off"
                : "Line wrapping off. Tap to turn on");
    }

    private void updateTitle() {
        title.setText(documentName + (dirty ? " •" : ""));
        title.setContentDescription(documentName + (dirty ? ", unsaved changes" : "")
                + ". Tap for file location.");
    }

    private void showKeyboard() {
        editor.removeCallbacks(showKeyboardRequest);
        keyboardRequestPending = true;
        editor.setShowSoftInputOnFocus(true);
        editor.requestFocus();
        editor.postDelayed(showKeyboardRequest, 250);
    }

    private void toggleKeyboard() {
        if (keyboardRequestPending || isKeyboardVisible()) hideKeyboard();
        else showKeyboard();
    }

    private void hideKeyboard() {
        editor.removeCallbacks(showKeyboardRequest);
        keyboardRequestPending = false;
        editor.setShowSoftInputOnFocus(false);
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                .hideSoftInputFromWindow(editor.getWindowToken(), 0);
    }

    private boolean isKeyboardVisible() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsets insets = root.getRootWindowInsets();
            return insets != null && insets.isVisible(WindowInsets.Type.ime());
        }
        Rect visible = new Rect();
        root.getWindowVisibleDisplayFrame(visible);
        return root.getRootView().getHeight() - visible.bottom > dp(150);
    }

    private void showError(String title, Exception error) {
        new AlertDialog.Builder(themedContext())
                .setTitle(title)
                .setMessage(error.getMessage() == null ? "Please try again." : error.getMessage())
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (busy) return;
        confirmDiscard(() -> MainActivity.super.onBackPressed());
    }

    @Override
    protected void onPause() {
        mainHandler.removeCallbacks(updateDraft);
        if (dirty) saveDraft();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        editor.removeCallbacks(showKeyboardRequest);
        io.shutdown();
        super.onDestroy();
    }

    private void saveDraft() {
        FileOutputStream stream = null;
        try {
            stream = draftFile.startWrite();
            DataOutputStream data = new DataOutputStream(stream);
            data.writeInt(DRAFT_VERSION);
            data.writeUTF(documentUri == null ? "" : documentUri.toString());
            data.writeUTF(documentName);
            data.writeBoolean(hasBom);
            byte[] text = editor.getText().toString().getBytes(StandardCharsets.UTF_8);
            data.writeInt(text.length);
            data.write(text);
            byte[] baseline = savedText.getBytes(StandardCharsets.UTF_8);
            data.writeInt(baseline.length);
            data.write(baseline);
            data.flush();
            draftFile.finishWrite(stream);
        } catch (IOException error) {
            if (stream != null) draftFile.failWrite(stream);
        }
    }

    private void restoreDraft() {
        try (DataInputStream data = new DataInputStream(draftFile.openRead())) {
            if (data.readInt() != DRAFT_VERSION) throw new IOException("Unknown draft version");
            String uri = data.readUTF();
            String name = data.readUTF();
            boolean bom = data.readBoolean();
            int length = data.readInt();
            if (length < 0 || length > MAX_OPEN_BYTES * 4) throw new IOException("Invalid draft length");
            byte[] bytes = new byte[length];
            data.readFully(bytes);
            int baselineLength = data.readInt();
            if (baselineLength < 0 || baselineLength > MAX_OPEN_BYTES * 4)
                throw new IOException("Invalid draft baseline length");
            byte[] baseline = new byte[baselineLength];
            data.readFully(baseline);
            documentUri = uri.isEmpty() ? null : Uri.parse(uri);
            documentName = name;
            documentPath = documentUri == null ? "Untitled" : queryPath(documentUri, name);
            hasBom = bom;
            setEditorText(new String(bytes, StandardCharsets.UTF_8));
            savedText = new String(baseline, StandardCharsets.UTF_8);
            dirty = !savedText.contentEquals(editor.getText());
        } catch (IOException error) {
            clearDraft();
        }
    }

    private void clearDraft() {
        draftFile.delete();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class OpenedDocument {
        final String text;
        final boolean hasBom;

        OpenedDocument(String text, boolean hasBom) {
            this.text = text;
            this.hasBom = hasBom;
        }
    }
}
