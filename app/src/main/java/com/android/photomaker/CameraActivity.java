package com.android.photomaker;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import static org.opencv.core.Core.flip;
import static org.opencv.core.Core.transpose;

public class CameraActivity extends AppCompatActivity implements Handler.Callback {

    private static final String TAG = "CameraActivity";

    private static ImageView camera2View;
    private static TextView camera2Parameter;
    private TextureView camera2TextureView;
    public Button captureButton;
    public Button switchButton;
    public Button saveButton;

    private HandlerThread mBackgroundThread;
    //background handler to handle all the camera and image process activities
    private Handler mBackgroundHandler;

    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private ImageReader mImageReader;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mLockFocusRequet;
    private CaptureRequest mPreviewRequest;

    private static final int BACK_CAMERA_ID = 0;
    private static final int FRONT_CAMERA_ID = 1;
    private static final int IMAGE_VIEW_UPDATE = 0;
    private static final int IMAGE_READY = 1;

    private int mCurrentCamera = FRONT_CAMERA_ID;

    private Size mPreviewSize;
    private Integer mSensorOrientation;
    private boolean mFocusLocked = false;
    private boolean mPhotoAvail = false;
    private boolean mCameraStarted = false;

    //handle the UI activities
    private Handler mUIHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg){
            int msgID = msg.what;
            switch (msgID) {
                case IMAGE_VIEW_UPDATE:
                {
                    Bitmap bitmap = (Bitmap)msg.obj;
                    camera2View.setImageBitmap(bitmap);
                    camera2View.setVisibility(View.VISIBLE);
                    mPhotoAvail = true;
                    closeCamera();
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
            case IMAGE_VIEW_UPDATE: {
                Mat mat_out_gray = new Mat();
                Mat mat_out_rgb = new Mat();
                Mat mYuvMat = (Mat)msg.obj;
                //Imgproc.cvtColor(mYuvMat, mat_out_gray, Imgproc.COLOR_YUV2GRAY_I420);
                Imgproc.cvtColor(mYuvMat, mat_out_rgb, Imgproc.COLOR_YUV2RGB_I420);
                Imgproc.cvtColor(mat_out_rgb, mat_out_gray, Imgproc.COLOR_RGB2GRAY);

                mat_out_gray = matRotateClockWise90(mat_out_gray);
                mat_out_rgb = matRotateClockWise90(mat_out_rgb);


                Rect face = PhotoUtils.faceDetect(mat_out_gray);

                //Draw the face rect area
                //Imgproc.rectangle(mat_out_rgb, face.tl(), face.br(), new Scalar(0, 255, 0, 255), 3);

                //resize
                int size = (int)(face.height*1.7);
                Mat mat1 = PhotoUtils.photoResize(mat_out_rgb, face, new Size(size,size));

                //edge detect
                Mat mask = PhotoUtils.faceSegmentWithWhiteBg(mat1);
                org.opencv.core.Core.bitwise_not(mask,mask);
                Mat output = PhotoUtils.ImageProcWithMask(mat1, mask);

                Bitmap bitmap = Bitmap.createBitmap(output.cols(), output.rows(), Bitmap.Config.ARGB_8888);
                org.opencv.android.Utils.matToBitmap(output, bitmap);
                mUIHandler.obtainMessage(IMAGE_VIEW_UPDATE,bitmap).sendToTarget();
                break;
            }
            default:
                break;
        }
        return true;
    }

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader ir) {
            // Get the next image from the queue
            Image image = ir.acquireNextImage();
            Log.d(TAG, "onImageAvailable: image height: " + image.getHeight() + ", image width: " + image.getWidth() +
                    ",image reader height: " + mImageReader.getHeight() + ",width: " + mImageReader.getWidth());
            Mat mYuvMat = PhotoUtils.imageToMat(image);

            mBackgroundHandler.obtainMessage(IMAGE_VIEW_UPDATE, mYuvMat).sendToTarget();
            image.close();
        }
    };

    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            Log.d(TAG,"onOpened");
            startPreview();
        }
        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            cameraDevice.close();
            mCameraDevice = null;
        }
        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            cameraDevice.close();
            Log.d(TAG,"onError");
            mCameraDevice = null;
        }
    };

    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureProgressed(CameraCaptureSession session,
                                        CaptureRequest request,
                                        CaptureResult partialResult) {
            Log.d(TAG,"onCaptureProgressed");
        }
        @Override
        public void onCaptureCompleted(CameraCaptureSession session,
                                       CaptureRequest request,
                                       TotalCaptureResult result) {
            Log.d(TAG,"onCaptureCompleted");
            if(mFocusLocked) {
                Log.d(TAG,"Lock Focus");
                //takePicture();
                if(request.equals(mLockFocusRequet)){
                    takePicture();
                }
            }
        }
    };

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            Log.d(TAG,"onSurfaceTextureAvailable");
            openCamera(mCurrentCamera);
        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {

        }
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        camera2View = findViewById(R.id.camera2_preview);
        camera2Parameter = findViewById(R.id.camera2_captures);
        camera2Parameter.setText("Camera information:");
        camera2TextureView = findViewById(R.id.camera2_texture);
        camera2TextureView.setSurfaceTextureListener(mSurfaceTextureListener);

        captureButton = findViewById(R.id.button3);
        switchButton = findViewById(R.id.button4);
        saveButton = findViewById(R.id.button5);

        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCameraStarted) {
                    lockFocus();
                }
            }
        });
        switchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchCamera();
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mPhotoAvail) {
                    camera2View.setVisibility(View.INVISIBLE);
                    openCamera(mCurrentCamera);
                    mPhotoAvail = false;
                }
            }
        });

        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper(),this);

    }

    @Override
    public void onResume() {
        super.onResume();
        if (camera2TextureView.isAvailable()) {
            openCamera(mCurrentCamera);
        } else {
            camera2TextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        super.onPause();
    }

    private void openCamera(int cameraID) {
        //to do: first check the app camera permission
        mCameraManager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);

        try {
            String[] cameraIDList = mCameraManager.getCameraIdList();
            CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraIDList[cameraID]);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if(android.os.Build.VERSION.SDK_INT >= 23) {
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            }
            mPreviewSize = Collections.max(
                    Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888)),
                    new CompareSizesByArea());

            mPreviewSize = new Size(900,1200);
            mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.YUV_420_888,2);
            //Log.d(TAG,mPreview size: width  + mPreviewSize.getWidth() +  height  + mPreviewSize.getHeight());
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
            mCameraManager.openCamera(cameraIDList[cameraID], mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        mCameraStarted = true;
    }
    private void closeCamera() {
        if (null != mCaptureSession) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }
        mCameraStarted = false;
    }

    private void startPreview() {
        try {
            SurfaceTexture texture = camera2TextureView.getSurfaceTexture();

            //camera2TextureView.getWidth(), camera2TextureView.getHeight()
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface surface = new Surface(texture);
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            mCaptureSession = cameraCaptureSession;
                            try {
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                // Flash is automatically enabled when necessary.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);
                                Log.d(TAG,"onConfigured complete");
                            }catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }
                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                            Log.d(TAG,"onConfigureFailed");
                        }
                    }, mBackgroundHandler);
        } catch (CameraAccessException e){
            e.printStackTrace();
        }
    }

    private void lockFocus(){
        try{
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            mLockFocusRequet = mPreviewRequestBuilder.build();
            mCaptureSession.capture(mLockFocusRequet, mCaptureCallback,
                    mBackgroundHandler);
            mFocusLocked = true;
            Log.d(TAG,"Lock Focus called");
        }catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void switchCamera(){
        if (mCurrentCamera == FRONT_CAMERA_ID) {
            mCurrentCamera = BACK_CAMERA_ID;
        } else {
            mCurrentCamera = FRONT_CAMERA_ID;
        }
        closeCamera();
        openCamera(mCurrentCamera);
    }

    private void takePicture() {
        try {
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session,
                                               CaptureRequest request,
                                               TotalCaptureResult result) {
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        }catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    Mat matRotateClockWise90(Mat src) {
        if (src.empty()) {
            Log.d(TAG,"RorateMat src is empty!");
        }
        flip(src, src, 0);
        transpose(src, src);
        if(mCurrentCamera == FRONT_CAMERA_ID) {
            flip(src, src, 0);
        }
        return src;
    }

    static{System.loadLibrary("opencv_java3"); }
}
