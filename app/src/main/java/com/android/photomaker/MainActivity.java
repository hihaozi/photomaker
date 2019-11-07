package com.android.photomaker;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.view.View;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    Button button_cali;
    Button button_load;
    Intent intent_cali;
    Intent intent_load;

    private String[] permissions = {Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        PhotoUtils.loadClassifier(getApplicationContext());
        checkPermissions();

        intent_cali = new Intent(this, CameraActivity.class);
        intent_load = new Intent(this, LoadImageActivity.class);
        button_cali = (Button) findViewById(R.id.button_capture);
        button_load = (Button) findViewById(R.id.button_load);
        button_cali.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(intent_cali,1);
            }
        });

        button_load.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(intent_load,1);
            }
        });
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        if ( (ContextCompat.checkSelfPermission(getApplicationContext(), permissions[0])
                != PackageManager.PERMISSION_GRANTED) ||
             (ContextCompat.checkSelfPermission(getApplicationContext(), permissions[1])
                     != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this, permissions, 321);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 321) {
            if (grantResults.length == permissions.length) {

            } else {
                Toast toast = Toast.makeText(this, "permission granted success", Toast.LENGTH_LONG);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

    }

    static{System.loadLibrary("opencv_java3"); }
}
