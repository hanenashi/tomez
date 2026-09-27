package com.hanenashi.tomez;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.text.Layout;
import android.util.AtomicFile;
import android.view.Gravity;
import android.view.Menu;
import android.view.SubMenu;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.ContextThemeWrapper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.ScrollView;
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

    private static final int NEW = 1;
    private static final int OPEN = 2;
    private static final int SAVE = 3;
    private static final int SAVE_AS = 4;
    private static final int CLOSE = 5;
    private static final int MODE_READ = 6;
    private static final int MODE_EDIT = 7;
    private static final int FONT_SANS = 10;
    private static final int FONT_SERIF = 11;
    private static final int FONT_MONO = 12;
    private static final int SIZE = 20;
    private static final int FULLSCREEN = 21;
    private static final int THEME_LIGHT = 30;
    private static final int THEME_DARK = 31;
    private static final int OLD_THEME_BLACK = 32;
    private static final int THEME_GREY = 33;
    private static final int THEME_GREEN = 34;
    private static final int CURSOR_THIN = 40;
    private static final int CURSOR_THICK = 41;
    private static final int CURSOR_BLOCK = 42;
    private static final int CURSOR_UNDERLINE = 43;
    private static final int GITHUB = 50;
    private static final int MIN_SLIDER_SIZE = 10;
    private static final int MAX_SLIDER_SIZE = 40;
    private static final int MIN_CUSTOM_SIZE = 8;
    private static final int MAX_CUSTOM_SIZE = 96;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TerminalEditText editor;
    private TextView readView;
    private ScrollView readScroll;
    private TextView title;
    private TextView modeLabel;
    private Button menuButton;
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
    private boolean editMode;
    private int modeGeneration;
    private Runnable afterSave;
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
        draftFile = new AtomicFile(new File(getFilesDir(), "unsaved-draft"));
        buildUi();
        restoreDraft();
        applyAppearance();
        updateTitle();
        setEditMode(false);
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
        title.setOnClickListener(view -> new AlertDialog.Builder(themedContext())
                .setTitle("File location")
                .setMessage(documentPath)
                .setPositiveButton("OK", null)
                .show());
        toolbarRow.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));

        modeLabel = new TextView(this);
        modeLabel.setText("READ");
        modeLabel.setTextSize(11);
        modeLabel.setGravity(Gravity.CENTER);
        toolbarRow.addView(modeLabel, new LinearLayout.LayoutParams(dp(48), dp(48)));

        menuButton = new Button(this);
        menuButton.setText("⚙");
        menuButton.setTextSize(26);
        menuButton.setAllCaps(false);
        menuButton.setContentDescription("Menu and settings");
        menuButton.setOnClickListener(this::showMenu);
        toolbarRow.addView(menuButton, new LinearLayout.LayoutParams(dp(56), dp(48)));
        root.addView(toolbarRow);

        divider = new View(this);
        root.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        readScroll = new ScrollView(this);
        readScroll.setFillViewport(true);
        readView = new TextView(this);
        readView.setGravity(Gravity.TOP | Gravity.START);
        readView.setPadding(dp(16), dp(12), dp(16), dp(16));
        readView.setTextIsSelectable(true);
        readView.setHint("Start typing…");
        readScroll.addView(readView);
        root.addView(readScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        editor = new TerminalEditText(this);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setPadding(dp(16), dp(12), dp(16), dp(16));
        editor.setBackgroundColor(Color.TRANSPARENT);
        editor.setSingleLine(false);
        editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editor.setHorizontallyScrolling(false);
        editor.setHint("Start typing…");
        editor.setTerminalCursorStyle(preferences.getInt("cursor", CURSOR_THIN));
        editor.setTerminalCursorEnabled(false);
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
        editor.setVisibility(View.GONE);
        root.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(themedContext(), anchor);
        Menu menu = popup.getMenu();
        SubMenu modes = menu.addSubMenu(0, 0, 0, "Mode");
        modes.add(300, MODE_READ, 0, "Read").setCheckable(true).setChecked(!editMode);
        modes.add(300, MODE_EDIT, 1, "Edit").setCheckable(true).setChecked(editMode);
        menu.add(0, NEW, 1, "New");
        menu.add(0, OPEN, 2, "Open…");
        menu.add(0, SAVE, 3, "Save");
        menu.add(0, SAVE_AS, 4, "Save As…");
        menu.add(0, CLOSE, 5, "Close");

        SubMenu fonts = menu.addSubMenu(0, 0, 6, "Font");
        int currentFont = preferences.getInt("font", FONT_SANS);
        fonts.add(100, FONT_SANS, 0, "System sans").setCheckable(true)
                .setChecked(currentFont == FONT_SANS);
        fonts.add(100, FONT_SERIF, 1, "System serif").setCheckable(true)
                .setChecked(currentFont == FONT_SERIF);
        fonts.add(100, FONT_MONO, 2, "Monospace").setCheckable(true)
                .setChecked(currentFont == FONT_MONO);

        menu.add(0, SIZE, 7, "Text size…");

        SubMenu themes = menu.addSubMenu(0, 0, 8, "Theme");
        int currentTheme = preferences.getInt("theme", THEME_LIGHT);
        themes.add(200, THEME_LIGHT, 0, "Light").setCheckable(true)
                .setChecked(currentTheme == THEME_LIGHT);
        themes.add(200, THEME_DARK, 1, "Dark").setCheckable(true)
                .setChecked(currentTheme == THEME_DARK);
        themes.add(200, THEME_GREY, 2, "Grey").setCheckable(true)
                .setChecked(currentTheme == THEME_GREY);
        themes.add(200, THEME_GREEN, 3, "Green (Matrix)").setCheckable(true)
                .setChecked(currentTheme == THEME_GREEN);

        SubMenu cursors = menu.addSubMenu(0, 0, 9, "Cursor");
        int currentCursor = preferences.getInt("cursor", CURSOR_THIN);
        cursors.add(400, CURSOR_THIN, 0, "Thin bar").setCheckable(true)
                .setChecked(currentCursor == CURSOR_THIN);
        cursors.add(400, CURSOR_THICK, 1, "Thick bar").setCheckable(true)
                .setChecked(currentCursor == CURSOR_THICK);
        cursors.add(400, CURSOR_BLOCK, 2, "Block").setCheckable(true)
                .setChecked(currentCursor == CURSOR_BLOCK);
        cursors.add(400, CURSOR_UNDERLINE, 3, "Underline").setCheckable(true)
                .setChecked(currentCursor == CURSOR_UNDERLINE);

        menu.add(0, FULLSCREEN, 10, "Fullscreen").setCheckable(true)
                .setChecked(preferences.getBoolean("fullscreen", false));
        menu.add(0, 0, 11, "tomez " + BuildConfig.VERSION_NAME).setEnabled(false);
        menu.add(0, GITHUB, 12, "github.com/hanenashi/tomez ↗");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            switch (id) {
                case NEW:
                case CLOSE:
                    confirmDiscard(this::newDocument);
                    return true;
                case OPEN:
                    confirmDiscard(this::launchOpen);
                    return true;
                case SAVE:
                    save(null);
                    return true;
                case SAVE_AS:
                    launchCreate();
                    return true;
                case SIZE:
                    showTextSizeDialog();
                    return true;
                case MODE_READ:
                    setEditMode(false);
                    return true;
                case MODE_EDIT:
                    setEditMode(true);
                    return true;
                case FULLSCREEN:
                    preferences.edit().putBoolean("fullscreen",
                            !preferences.getBoolean("fullscreen", false)).apply();
                    applySystemUi();
                    return true;
                case GITHUB:
                    openGithub();
                    return true;
                default:
                    if (id >= FONT_SANS && id <= FONT_MONO)
                        preferences.edit().putInt("font", id).apply();
                    else if (id == THEME_LIGHT || id == THEME_DARK
                            || id == THEME_GREY || id == THEME_GREEN)
                        preferences.edit().putInt("theme", id).apply();
                    else if (id >= CURSOR_THIN && id <= CURSOR_UNDERLINE)
                        preferences.edit().putInt("cursor", id).apply();
                    else return false;
                    applyAppearance();
                    return true;
            }
        });
        popup.show();
    }

    private void showTextSizeDialog() {
        Context context = themedContext();
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(8), dp(24), 0);

        TextView help = new TextView(context);
        help.setText("Slide from 10–40 sp, or enter 8–96 sp.");
        content.addView(help);

        SeekBar slider = new SeekBar(context);
        slider.setMax(MAX_SLIDER_SIZE - MIN_SLIDER_SIZE);
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
        content.addView(manual);

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
                } catch (NumberFormatException ignored) { }
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Text size")
                .setView(content)
                .setPositiveButton("Apply", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
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
                    applyAppearance();
                    dialog.dismiss();
                }));
        dialog.show();
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
        toolbar.setBackgroundColor(surface);
        divider.setBackgroundColor(line);
        title.setTextColor(foreground);
        modeLabel.setTextColor(muted);
        editor.setTextColor(foreground);
        editor.setHintTextColor(muted);
        editor.setHighlightColor(Color.argb(90, Color.red(accent), Color.green(accent), Color.blue(accent)));
        readView.setTextColor(foreground);
        readView.setHintTextColor(muted);
        readView.setHighlightColor(Color.argb(90, Color.red(accent), Color.green(accent), Color.blue(accent)));
        int font = preferences.getInt("font", FONT_SANS);
        Typeface typeface = font == FONT_SERIF ? Typeface.SERIF
                : font == FONT_MONO ? Typeface.MONOSPACE : Typeface.SANS_SERIF;
        editor.setTypeface(typeface);
        readView.setTypeface(typeface);
        int size = preferences.getInt("size", 18);
        editor.setTextSize(size);
        readView.setTextSize(size);
        editor.setTerminalCursorColor(accent);
        editor.setTerminalCursorStyle(preferences.getInt("cursor", CURSOR_THIN));
        menuButton.setTextColor(foreground);
        menuButton.setBackgroundTintList(ColorStateList.valueOf(surface));
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
        documentUri = null;
        documentName = "Untitled";
        documentPath = "Untitled";
        hasBom = false;
        setEditorText("");
        clearDraft();
        setEditMode(true);
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
                    setEditMode(false);
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
                    String root = "primary".equalsIgnoreCase(volume)
                            ? "/storage/emulated/0" : "/storage/" + volume;
                    return root + "/" + relative;
                }
            } catch (IllegalArgumentException ignored) { }
        }
        if ("file".equals(uri.getScheme()) && uri.getPath() != null)
            return uri.getPath();
        // Other document providers can use opaque IDs rather than filesystem paths.
        return name + " · " + uri;
    }

    private void setEditorText(String text) {
        suppressChanges = true;
        editor.setText(text);
        editor.setSelection(0);
        editor.scrollTo(0, 0);
        suppressChanges = false;
        readView.setText(text);
        readScroll.scrollTo(0, 0);
        savedText = text;
        dirty = false;
        updateTitle();
    }

    private void setBusy(boolean value) {
        busy = value;
        editor.setEnabled(!value);
        menuButton.setEnabled(!value);
    }

    private void updateTitle() {
        title.setText(documentPath + (dirty ? " •" : ""));
        title.setContentDescription(documentPath + (dirty ? ", unsaved changes" : ""));
    }

    private void setEditMode(boolean editing) {
        if (editMode == editing && editor.getVisibility() == (editing ? View.VISIBLE : View.GONE))
            return;
        int scrollY = editMode ? editor.getScrollY() : readScroll.getScrollY();
        int visibleOffset = editor.getSelectionStart();
        if (editing) {
            Layout layout = readView.getLayout();
            if (layout != null) {
                int line = layout.getLineForVertical(scrollY + dp(12));
                visibleOffset = Math.min(editor.length(), layout.getLineStart(line));
            }
        }
        editMode = editing;
        int generation = ++modeGeneration;
        modeLabel.setText(editing ? "EDIT" : "READ");
        editor.setTerminalCursorEnabled(editing);
        if (editing) {
            editor.setVisibility(View.VISIBLE);
            readScroll.setVisibility(View.GONE);
            editor.requestFocus();
            int targetOffset = visibleOffset;
            editor.post(() -> {
                if (editMode && modeGeneration == generation) editor.setSelection(targetOffset);
            });
            editor.postDelayed(() -> {
                if (!editMode || modeGeneration != generation) return;
                ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                        .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
            }, 250);
            editor.postDelayed(() -> {
                if (!editMode || modeGeneration != generation
                        || editor.getSelectionStart() != targetOffset) return;
                editor.scrollTo(0, scrollY);
                editor.bringPointIntoView(targetOffset);
            }, 550);
        } else {
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(editor.getWindowToken(), 0);
            editor.clearFocus();
            readView.setText(editor.getText().toString());
            readScroll.setVisibility(View.VISIBLE);
            editor.setVisibility(View.GONE);
            readScroll.post(() -> readScroll.scrollTo(0, scrollY));
        }
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
