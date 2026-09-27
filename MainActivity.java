package com.openai.pixelcleaner;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.openai.pixelcleaner.core.PixelCleanerCore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int OPEN_IMAGE = 2001;

    // Resource-exhaustion guardrails. 10 MP still covers 4K (3840x2160) images.
    private static final long MAX_FILE_BYTES = 128L * 1024L * 1024L;
    private static final long MAX_PIXELS = 10_000_000L;
    private static final int MAX_DIMENSION = 8192;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ZoomImageView preview;
    private TextView status;
    private TextView noiseLabel, sharpenLabel, fringeLabel;
    private SeekBar noiseSeek, sharpenSeek, fringeSeek;
    private Switch fringeSwitch;
    private Button processButton, saveButton, compareButton;
    private Bitmap original;
    private Bitmap result;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        applyDefaults();
    }

    private View buildUi() {
        int pad = dp(14);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("Pixel Cleaner");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(28, 28, 28));
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setText("PNG/JPG 이미지를 열어 주세요.");
        status.setTextSize(14);
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        preview = new ZoomImageView(this);
        preview.setBackground(new CheckerboardDrawable(dp(14)));
        LinearLayout.LayoutParams imageLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        imageLp.topMargin = dp(10);
        imageLp.bottomMargin = dp(10);
        root.addView(preview, imageLp);

        ScrollView controlsScroll = new ScrollView(this);
        controlsScroll.setFillViewport(true);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controlsScroll.addView(controls, new ScrollView.LayoutParams(-1, -2));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button open = makeButton("이미지 열기");
        processButton = makeButton("처리");
        saveButton = makeButton("PNG 저장");
        buttons.addView(open, weighted());
        buttons.addView(processButton, weighted());
        buttons.addView(saveButton, weighted());
        controls.addView(buttons);

        noiseLabel = makeLabel("");
        controls.addView(noiseLabel);
        noiseSeek = makeSeekBar();
        controls.addView(noiseSeek);

        LinearLayout noisePresets = new LinearLayout(this);
        noisePresets.setOrientation(LinearLayout.HORIZONTAL);
        Button noiseLow = makeButton("약하게 8");
        Button noiseDefault = makeButton("기본 15");
        Button noiseStrong = makeButton("강하게 25");
        noisePresets.addView(noiseLow, weighted());
        noisePresets.addView(noiseDefault, weighted());
        noisePresets.addView(noiseStrong, weighted());
        controls.addView(noisePresets);

        sharpenLabel = makeLabel("");
        controls.addView(sharpenLabel);
        sharpenSeek = makeSeekBar();
        controls.addView(sharpenSeek);

        fringeSwitch = new Switch(this);
        fringeSwitch.setText("외곽의 흐린 픽셀 노이즈 정리");
        fringeSwitch.setTextSize(15);
        fringeSwitch.setPadding(0, dp(5), 0, 0);
        controls.addView(fringeSwitch);

        fringeLabel = makeLabel("");
        controls.addView(fringeLabel);
        fringeSeek = makeSeekBar();
        controls.addView(fringeSeek);

        compareButton = makeButton("누르는 동안 원본 보기");
        controls.addView(compareButton, new LinearLayout.LayoutParams(-1, -2));

        TextView note = makeLabel("노이즈 제거·샤프닝·외곽 정리는 0~100 조절 가능 · 기본값 15 / 15 / 35 · 픽셀 크기와 캔버스는 변경하지 않습니다.");
        note.setTextSize(12);
        controls.addView(note);

        TextView securityNote = makeLabel("오프라인 처리 · 네트워크/저장소 전체 접근 권한 없음 · 최대 10 MP 입력");
        securityNote.setTextSize(11);
        controls.addView(securityNote);

        root.addView(controlsScroll, new LinearLayout.LayoutParams(-1, -2));

        open.setOnClickListener(v -> openImage());
        processButton.setOnClickListener(v -> processImage());
        saveButton.setOnClickListener(v -> saveResult());
        noiseLow.setOnClickListener(v -> noiseSeek.setProgress(8));
        noiseDefault.setOnClickListener(v -> noiseSeek.setProgress(15));
        noiseStrong.setOnClickListener(v -> noiseSeek.setProgress(25));

        SeekBar.OnSeekBarChangeListener labelUpdater = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { updateLabels(); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
        noiseSeek.setOnSeekBarChangeListener(labelUpdater);
        sharpenSeek.setOnSeekBarChangeListener(labelUpdater);
        fringeSeek.setOnSeekBarChangeListener(labelUpdater);
        fringeSwitch.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            fringeSeek.setEnabled(isChecked);
            fringeLabel.setAlpha(isChecked ? 1f : 0.45f);
        });

        compareButton.setOnTouchListener((v, event) -> {
            if (original == null || result == null) return false;
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                preview.setImageDrawable(new BitmapDrawable(getResources(), original));
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                preview.setImageDrawable(new BitmapDrawable(getResources(), result));
                return true;
            }
            return true;
        });

        return root;
    }

    private void applyDefaults() {
        noiseSeek.setProgress(15);
        sharpenSeek.setProgress(15);
        fringeSeek.setProgress(35);
        fringeSwitch.setChecked(true);
        processButton.setEnabled(false);
        saveButton.setEnabled(false);
        compareButton.setEnabled(false);
        updateLabels();
    }

    private void updateLabels() {
        noiseLabel.setText("노이즈 제거: " + noiseSeek.getProgress() + " / 100");
        sharpenLabel.setText("샤프닝: " + sharpenSeek.getProgress() + " / 100");
        fringeLabel.setText("외곽 픽셀 정리: " + fringeSeek.getProgress() + " / 100");
    }

    private void openImage() {
        if (busy) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, OPEN_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != OPEN_IMAGE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        setBusy(true);
        status.setText("이미지 검사 중…");

        executor.execute(() -> {
            try {
                Bitmap bmp = decodeImageSafely(uri);
                runOnUiThread(() -> {
                    setOriginal(bmp);
                    setBusy(false);
                });
            } catch (SafeImageException e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    toast(e.getMessage());
                    status.setText("이미지를 열지 못했습니다.");
                });
            } catch (SecurityException e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    toast("선택한 이미지에 접근할 수 없습니다.");
                    status.setText("이미지를 열지 못했습니다.");
                });
            } catch (OutOfMemoryError e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    toast("이미지가 현재 기기의 처리 한도를 초과했습니다.");
                    status.setText("메모리 한도로 이미지를 열지 못했습니다.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    toast("안전하게 해석할 수 없는 이미지입니다.");
                    status.setText("이미지를 열지 못했습니다.");
                });
            }
        });
    }

    private Bitmap decodeImageSafely(Uri uri) throws IOException {
        if (uri == null || !ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(uri.getScheme())) {
            throw new SafeImageException("문서 선택기로 제공된 이미지 파일만 열 수 있습니다.");
        }

        ContentResolver resolver = getContentResolver();
        String claimedMime = resolver.getType(uri);
        if (claimedMime != null && !claimedMime.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new SafeImageException("이미지 형식의 파일만 열 수 있습니다.");
        }

        try (AssetFileDescriptor afd = resolver.openAssetFileDescriptor(uri, "r")) {
            if (afd == null) throw new SafeImageException("이미지 파일을 열 수 없습니다.");
            long length = afd.getLength();
            if (length > MAX_FILE_BYTES) {
                throw new SafeImageException("파일이 너무 큽니다. 128 MB 이하 이미지를 사용해 주세요.");
            }
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new SafeImageException("이미지 파일을 읽을 수 없습니다.");
            BitmapFactory.decodeStream(in, null, bounds);
        }

        validateDimensions(bounds.outWidth, bounds.outHeight);
        if (bounds.outMimeType == null || !bounds.outMimeType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new SafeImageException("지원되는 이미지 형식이 아닙니다.");
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        opts.inMutable = false;
        Bitmap bmp;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new SafeImageException("이미지 파일을 읽을 수 없습니다.");
            bmp = BitmapFactory.decodeStream(in, null, opts);
        }
        if (bmp == null) throw new SafeImageException("이미지를 디코딩할 수 없습니다.");

        try {
            validateDimensions(bmp.getWidth(), bmp.getHeight());
            if (bmp.getWidth() != bounds.outWidth || bmp.getHeight() != bounds.outHeight) {
                throw new SafeImageException("파일 정보가 처리 중 변경되어 열기를 중단했습니다.");
            }
        } catch (IOException e) {
            if (!bmp.isRecycled()) bmp.recycle();
            throw e;
        }
        return bmp;
    }

    private static void validateDimensions(int width, int height) throws SafeImageException {
        if (width <= 0 || height <= 0) {
            throw new SafeImageException("이미지 크기 정보를 확인할 수 없습니다.");
        }
        long pixels = (long) width * (long) height;
        if (width > MAX_DIMENSION || height > MAX_DIMENSION || pixels > MAX_PIXELS) {
            throw new SafeImageException("이미지는 최대 8192 px 한 변, 총 10 MP까지 처리할 수 있습니다.");
        }
    }

    private void setOriginal(Bitmap bmp) {
        recycleSafely(original, bmp);
        recycleSafely(result, null);
        original = bmp;
        result = null;
        preview.setImageDrawable(new BitmapDrawable(getResources(), original));
        status.setText("원본 " + bmp.getWidth() + " × " + bmp.getHeight() + " px");
        processButton.setEnabled(true);
        saveButton.setEnabled(false);
        compareButton.setEnabled(false);
    }

    private void processImage() {
        if (busy || original == null) return;
        setBusy(true);
        final Bitmap source = original;
        final int noise = noiseSeek.getProgress();
        final int sharp = sharpenSeek.getProgress();
        final int fringe = fringeSwitch.isChecked() ? fringeSeek.getProgress() : 0;

        executor.execute(() -> {
            try {
                Bitmap cleaned = ImageProcessor.process(source, noise, sharp, fringe,
                        (stage, percent) -> runOnUiThread(() -> status.setText(stage + " · " + percent + "%")));
                runOnUiThread(() -> {
                    recycleSafely(result, cleaned);
                    result = cleaned;
                    preview.setImageDrawable(new BitmapDrawable(getResources(), result));
                    status.setText("완료 · " + result.getWidth() + " × " + result.getHeight() + " px · 노이즈 " + noise);
                    saveButton.setEnabled(true);
                    compareButton.setEnabled(true);
                    setBusy(false);
                });
            } catch (OutOfMemoryError e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    toast("처리 메모리가 부족합니다. 더 작은 이미지를 사용해 주세요.");
                });
            } catch (RuntimeException e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    toast("이미지 처리에 실패했습니다.");
                });
            }
        });
    }

    private void saveResult() {
        if (busy || result == null) return;
        setBusy(true);
        final Bitmap toSave = result;
        executor.execute(() -> {
            ContentResolver resolver = getContentResolver();
            Uri pendingUri = null;
            boolean published = false;
            String name = null;
            try {
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                name = "PixelCleaner_" + stamp + ".png";
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PixelCleaner");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
                pendingUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (pendingUri == null) throw new IOException("insert failed");

                try (OutputStream out = resolver.openOutputStream(pendingUri, "w")) {
                    if (out == null || !toSave.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        throw new IOException("png write failed");
                    }
                    out.flush();
                }

                values.clear();
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                if (resolver.update(pendingUri, values, null, null) <= 0) {
                    throw new IOException("publish failed");
                }
                published = true;
                String savedName = name;
                runOnUiThread(() -> {
                    setBusy(false);
                    status.setText("저장 완료 · Pictures/PixelCleaner/" + savedName);
                    toast("PNG로 저장했습니다.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    toast("PNG 저장에 실패했습니다.");
                });
            } finally {
                if (!published && pendingUri != null) {
                    try {
                        resolver.delete(pendingUri, null, null);
                    } catch (RuntimeException ignored) {
                        // Best-effort cleanup. No path or provider detail is exposed to the UI/log.
                    }
                }
            }
        });
    }

    private void setBusy(boolean value) {
        busy = value;
        processButton.setEnabled(!value && original != null);
        saveButton.setEnabled(!value && result != null);
        compareButton.setEnabled(!value && result != null);
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        return b;
    }

    private TextView makeLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(15);
        t.setTextColor(Color.rgb(45, 45, 45));
        t.setPadding(0, dp(6), 0, 0);
        return t;
    }

    private SeekBar makeSeekBar() {
        SeekBar s = new SeekBar(this);
        s.setMax(100);
        return s;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private static void recycleSafely(Bitmap oldBitmap, Bitmap keep) {
        if (oldBitmap != null && oldBitmap != keep && !oldBitmap.isRecycled()) oldBitmap.recycle();
    }

    @Override
    protected void onDestroy() {
        // A running worker may still be reading a bitmap. Do not recycle here; let Android/GC
        // release bitmap memory after the Activity and worker references disappear.
        executor.shutdownNow();
        super.onDestroy();
    }

    private static final class SafeImageException extends IOException {
        SafeImageException(String message) {
            super(message);
        }
    }
}
