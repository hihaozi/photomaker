package com.android.photomaker;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;
import java.io.FileInputStream;

import java.io.IOException;


public class LoadImageActivity extends AppCompatActivity implements Handler.Callback {
    private static final String TAG = "CameraActivity";

    Button button_load;
    private static ImageView camera2View;
    private Looper mLooper;
    private Handler mHandler;

    private final static int MSG_BITMAP_READY = 0;
    private final static int MSG_VIEW_UPDATE = 1;

    private Handler mUIHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg){
            int msgID = msg.what;
            switch (msgID) {
                case MSG_VIEW_UPDATE:
                {
                    Bitmap bitmap = (Bitmap)msg.obj;
                    camera2View.setImageBitmap(bitmap);
                    break;
                }
                default:break;
            }
        }
    };

    @Override
    public boolean handleMessage(Message msg) {
        int msgID = msg.what;

        switch (msgID) {
            case MSG_BITMAP_READY:
            {
                String filePath = (String)msg.obj;
                try {
                    int Orientation = getAttributeInt(filePath);
                    Log.d(TAG, "Image file path: " + filePath +  "Image orientation: " + Orientation);
                    FileInputStream is = new FileInputStream(filePath);
                    Bitmap bitmap  = BitmapFactory.decodeStream(is);
                    Mat mat_out_gray = new Mat();
                    Mat mat_out_rgb = new Mat();
                    org.opencv.android.Utils.bitmapToMat(bitmap, mat_out_rgb);

                    Imgproc.cvtColor(mat_out_rgb, mat_out_gray, Imgproc.COLOR_RGB2GRAY);

                    mat_out_rgb = PhotoUtils.matRotate(mat_out_rgb, Orientation);
                    mat_out_gray = PhotoUtils.matRotate(mat_out_gray, Orientation);

                    /*Rect[] facesArray = PhotoUtils.faceDetect1(mat_out_gray);
                    for (Rect item : facesArray) {
                        Imgproc.rectangle(mat_out_rgb, item.tl(), item.br(), new Scalar(0, 255, 0, 255), 3);
                    }
                    Mat output = mat_out_rgb;*/

                    Rect face = PhotoUtils.faceDetect(mat_out_gray);
                    int size = (int)(face.height*2);
                    Mat mat1 = PhotoUtils.photoResize(mat_out_rgb, face, new Size(size,size));

                    //edge detect
                    Mat mask = PhotoUtils.faceSegmentWithWhiteBg(mat1);

                    org.opencv.core.Core.bitwise_not(mask,mask);
                    Mat output = PhotoUtils.ImageProcWithMask(mat1, mask);
                    Bitmap bitmap1 = Bitmap.createBitmap(output.cols(), output.rows(), Bitmap.Config.ARGB_8888);
                    org.opencv.android.Utils.matToBitmap(output, bitmap1);
                    mUIHandler.obtainMessage(MSG_VIEW_UPDATE,bitmap1).sendToTarget();
                }catch (IOException e) {
                }
                break;
            }
            default:
        }
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load_image);
        button_load = (Button) findViewById(R.id.button_load1);
        camera2View = findViewById(R.id.camera2_preview1);

        HandlerThread t = new HandlerThread(TAG);
        t.start();
        mLooper = t.getLooper();
        mHandler = new Handler(mLooper, this);

        button_load.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_PICK);
                startActivityForResult(Intent.createChooser(intent, "Select Picture"), 1001);
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data){
        if (requestCode == 1001) {
            Uri selectedImage = data.getData();
            String[] filePathColumn = { MediaStore.Images.Media.DATA };

            Cursor cursor = getContentResolver().query(selectedImage,
                    filePathColumn, null, null, null);
            cursor.moveToFirst();

            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String filePath = cursor.getString(columnIndex);
            if (filePath != null) {
                mHandler.obtainMessage(MSG_BITMAP_READY, filePath).sendToTarget();
            }
            cursor.close();
        }
    }

    private int getAttributeInt(String filePath) {
        ExifInterface exif = null;
        try {
            exif = new ExifInterface(filePath);
        } catch (IOException e) {
            e.printStackTrace();
            exif = null;
        }
        return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED);
    }
}