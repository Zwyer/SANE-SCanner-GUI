package com.zhangwy.scannerapp;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class ShowImage extends AppCompatActivity {
    private ImageView imageView;
    private Button back_btn,save_btn;

    public Bitmap currentBitmap;
    public String imagepath,file_type;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_show_image);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        imageView = findViewById(R.id.imageView);
        imagepath = getIntent().getStringExtra("imagepath");
        file_type = getIntent().getStringExtra("file_type");
        back_btn = findViewById(R.id.Back);
        save_btn = findViewById(R.id.Save);
        if (imagepath != null) {
            File imageFile = new File(imagepath);
            if(imageFile.exists()){
                currentBitmap = BitmapFactory.decodeFile(imagepath);
                imageView.setImageBitmap(currentBitmap);
            }
        } else {
            Toast.makeText(this, "图片加载失败,图片地址:" + imagepath, Toast.LENGTH_SHORT).show();
            finish();
        }
        back_btn.setOnClickListener(v->{
            back_cb();
        });
        save_btn.setOnClickListener(v -> attemptSaveImage());
    }

    private void back_cb(){
        finish();
    }

    private void attemptSaveImage() {
        if (currentBitmap == null || currentBitmap.isRecycled()) {
            Toast.makeText(this, "没有可保存的图片", Toast.LENGTH_SHORT).show();
            return;
        }

        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    2
            );
            return;
        }

        saveImageToGallery();
    }

    private void saveImageToGallery() {
        String savedPath;
        savedPath = saveImageWithMediaStore();

        if (savedPath != null) {
            Toast.makeText(this, "图片已保存到相册", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
        }
    }

    private String saveImageWithMediaStore() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_" + System.currentTimeMillis() + "." + file_type);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/" + file_type);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Scanner");

        ContentResolver resolver = getContentResolver();
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        if (uri != null) {
            try (OutputStream out = resolver.openOutputStream(uri)) {
                if (out != null && currentBitmap != null) {
                    currentBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                    return uri.toString();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveImageToGallery();
            } else {
                saveImageToGallery();
//                Toast.makeText(this, "需要存储权限才能保存图片", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 回收Bitmap内存
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
            currentBitmap = null;
        }
    }
}

