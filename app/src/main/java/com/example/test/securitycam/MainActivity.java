package com.example.test.securitycam;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextureView mTextureView;
    private CameraManager mCameraManager;
    private int mCameraFacing;
    private String mCameraId;
    private Size mPreviewSize;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private CameraDevice mCameraDevice;
    private TextureView.SurfaceTextureListener mSurfaceTextureListener;
    private CameraDevice.StateCallback mStateCallback;
    private CameraCaptureSession mCameraCaptureSession;
    private Button mPhotoButton;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private File mGalleryFolder;
    private final int CAMERA_REQUEST_CODE = 200;
    private Intent mCameraService;
    private File mImageFile;

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();
            Log.d("--","--Received broadcast from service");
            if (bundle != null) {
                if (bundle.getInt("resultCode") == 1) {
                   //setUpCamera();
                    //openCamera();
                    onTakePhoto();
                }
            }
        }
    };

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0,90);
        ORIENTATIONS.append(Surface.ROTATION_90,0);
        ORIENTATIONS.append(Surface.ROTATION_180,270);
        ORIENTATIONS.append(Surface.ROTATION_270,180);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE}, CAMERA_REQUEST_CODE);

        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        mCameraFacing = CameraCharacteristics.LENS_FACING_BACK;

        mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {

            // Let us know when our TextureView and the core class laying beneath it (SurfaceTexture) are loaded.
            // Then we’ll set up our camera and open it.
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                setUpCamera();
                openCamera();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                return false;
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {}

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {}

        };


        mTextureView = findViewById(R.id.textureview);
        mPhotoButton = findViewById(R.id.getpicture);

        // There are three states that camera can be in

        mStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice cameraDevice) {
                MainActivity.this.mCameraDevice = cameraDevice;
                createPreviewSession();
            }

            @Override
            public void onDisconnected(CameraDevice cameraDevice) {
                cameraDevice.close();
                MainActivity.this.mCameraDevice = null;
            }

            @Override
            public void onError(CameraDevice cameraDevice, int error) {
                cameraDevice.close();
                MainActivity.this.mCameraDevice = null;
            }
        };

        // start and trigger a service
        mCameraService = new Intent(this, CameraService.class);
        // potentially add data to the intent
        //i.putExtra("KEY1", "Value to be used by the service");
        startService(mCameraService);

    }

    private void createImageGallery() {
        File storageDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        mGalleryFolder = new File(storageDirectory, getResources().getString(R.string.app_name));
        if (!mGalleryFolder.exists()) {
            boolean wasCreated = mGalleryFolder.mkdirs();
            if (!wasCreated) {
                Log.e("CapturedImages", "Failed to create directory");
            }
        }
    }

    private File createImageFile(File galleryFolder) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "image_" + timeStamp + "_";
        mImageFile = File.createTempFile(imageFileName, ".jpg", galleryFolder);
        Log.d("--", "File is " + mImageFile.getAbsolutePath());
        return mImageFile;
    }

    private void setUpCamera() {
        try {

            // go through a list of available cameras
            for (String cameraId : mCameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics =
                        mCameraManager.getCameraCharacteristics(cameraId);

                // find the facing camera
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == mCameraFacing) {
                    StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(
                            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                    // retrieve info about available preview sizes to scale our TextureView accordingly
                    // the zeroth element is the resolution we want  —  the highest available one.
                    mPreviewSize = streamConfigurationMap.getOutputSizes(SurfaceTexture.class)[0];
                    mCameraId = cameraId;

                    Log.d("--", "Setting up camera");
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        try {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                mCameraManager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
                Log.d("--", "Opening camera");
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // creates background thread with looper
    private void openBackgroundThread() {
        mBackgroundThread = new HandlerThread("camera_background_thread");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void createPreviewSession() {
        try {
            SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface previewSurface = new Surface(surfaceTexture);
            //final CaptureRequest.Builder captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mCaptureRequestBuilder.addTarget(previewSurface);

            mCameraDevice.createCaptureSession(Collections.singletonList(previewSurface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            if (mCameraDevice == null) {
                                return;
                            }

                            try {
                                CaptureRequest captureRequest = mCaptureRequestBuilder.build();
                                mCameraCaptureSession = cameraCaptureSession;
                                mCameraCaptureSession.setRepeatingRequest(captureRequest,
                                        null, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {

                        }
                    }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // register br
        registerReceiver(receiver, new IntentFilter(
                CameraService.NOTIFICATION));

        openBackgroundThread();
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);

        mPhotoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onTakePhoto();
            }
        });
    }

    public void onTakePhoto() {
        Toast.makeText(MainActivity.this,
                "Photo taken",
                Toast.LENGTH_SHORT).show();
        Log.d("--","--Photo taken");
        createImageGallery();
        lock();
        FileOutputStream outputPhoto = null;
        try {
            outputPhoto = new FileOutputStream(createImageFile(mGalleryFolder));
            mTextureView.getBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, outputPhoto);
            sendMail();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            unlock();
            try {
                if (outputPhoto != null) {
                    outputPhoto.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            //this.finish();
            //closeCamera();
        }
    }

    private void sendMail() {

        try {
            Log.d("--", "Trying to send email");
            GMailSender sender = new GMailSender("someone_@somewhere_.com", "somepassword");
            sender.sendMailAsync(mImageFile);
        } catch (Exception e) {
            Log.e("SendMail", e.getMessage(), e);
        }

    }

    private void lock() {
        try {
            mCameraCaptureSession.capture(mCaptureRequestBuilder.build(),
                    null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void unlock() {
        try {
            mCameraCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(),
                    null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    @Override
    protected void onStop() {
        super.onStop();
        closeCamera();
        closeBackgroundThread();
    }

    private void closeCamera() {
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }

        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    private void closeBackgroundThread() {
        if (mBackgroundHandler != null) {
            mBackgroundThread.quitSafely();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        }
    }

    @Override
    protected void onDestroy() {
        stopService(mCameraService);
        super.onDestroy();
    }
}
