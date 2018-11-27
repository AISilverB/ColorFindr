package cs173.colorfindr;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class GameActivity extends AppCompatActivity {

    private String TAG = "GameActivity";
    private TextureView textureView;
    private String UnlockedColors[];
    private String ColorList[];
    private int currctr;
    private String currcolor;
    private int currscore;
    private int currlives;
    private int currhighscore;

    //Check state orientation of output image
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static{
        ORIENTATIONS.append(Surface.ROTATION_0,90);
        ORIENTATIONS.append(Surface.ROTATION_90,0);
        ORIENTATIONS.append(Surface.ROTATION_180,270);
        ORIENTATIONS.append(Surface.ROTATION_270,180);
    }

    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSessions;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private int level;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            cameraDevice.close();
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        init();

        textureView = findViewById(R.id.textureView);
        //From Java 1.4 , you can use keyword 'assert' to check expression true or false
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);
        ImageButton btnCapture = findViewById(R.id.btnCapture);
        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePicture();
            }
        });
    }

    private void init(){
        level = 40;
        currscore = 0;
        currlives = 10;
        currhighscore = 0;
        currctr = -1;
        retrieveColorList();
        changeColor();
    }

    private void retrieveColorList(){
       ColorList = new String[127];
       ColorList = getResources().getStringArray(R.array.colorlist);
       currctr = 0;
       UnlockedColors = new String[127];
    }
    private void takePicture() {
        if(cameraDevice == null)
            return;
        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try{
            CameraCharacteristics characteristics = null;
            if (manager != null) {
                characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            }
            Size[] jpegSizes = null;
            if(characteristics != null)
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        .getOutputSizes(ImageFormat.JPEG);

            TextureView imagex = findViewById(R.id.textureView);

            //Capture image with custom size
            logme("textw", imagex.getWidth());
            logme("texth", imagex.getHeight());
            int width = imagex.getWidth();
            int height = imagex.getHeight();

            final ImageReader reader = ImageReader.newInstance(width,height,ImageFormat.JPEG,1);
            List<Surface> outputSurface = new ArrayList<>(2);
            outputSurface.add(reader.getSurface());
            outputSurface.add(new Surface(textureView.getSurfaceTexture()));

            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            //Check orientation base on device
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION,ORIENTATIONS.get(rotation));

            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    Image image = null;
                    try{
                        image = reader.acquireLatestImage();

                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);

                        Bitmap bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
                        Matrix m = new Matrix();
                        m.postRotate(90);
                        Bitmap bmp = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
                        imageAnswer(bmp);

                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        {
                            if(image != null)
                                image.close();
                        }
                    }
                }

            };

            reader.setOnImageAvailableListener(readerListener,mBackgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    createCameraPreview();
                }
            };

            cameraDevice.createCaptureSession(outputSurface, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    try{
                        cameraCaptureSession.capture(captureBuilder.build(),captureListener,mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                }
            },mBackgroundHandler);


        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        changeColor();
    }

    @SuppressLint("ResourceType")
    private void changeColor() {

        Random rand = new Random();

        int randomColor = rand.nextInt(ColorList.length);

        while(!Arrays.asList(UnlockedColors).contains(UnlockedColors[randomColor])){
            randomColor = rand.nextInt(ColorList.length);
        }
        currctr++;
        UnlockedColors[currctr] = ColorList[randomColor];

        EditText edit = findViewById(R.id.curr_color_disp);
        GradientDrawable gradientDrawable = (GradientDrawable) edit.getBackground().mutate();


        String colval = UnlockedColors[currctr].replaceAll("[^A-Za-z0-9]", "");
        Log.d(TAG, colval);
        int objid = this.getResources().getIdentifier(colval, "color", this.getPackageName());
        gradientDrawable.setColor(ContextCompat.getColor(this, objid));

        TextView boxtext = findViewById(R.id.curr_color_name);
        boxtext.setText(UnlockedColors[currctr]);
        currcolor = getResources().getString(objid).substring(2);

        TextView scoretext = findViewById(R.id.curr_score);
        scoretext.setText(Integer.toString(currscore));
        TextView livestext = findViewById(R.id.curr_lives);
        livestext.setText(Integer.toString(currlives));
        TextView highscoretext = findViewById(R.id.curr_high_score);
        highscoretext.setText(Integer.toString(currhighscore));
    }

    private void imageAnswer(Bitmap imagea) {
        int[] imgloc = new int[2];
        TextureView imagex = findViewById(R.id.textureView);
        imagex.getLocationOnScreen(imgloc);

        int[] boxloc = new int[2];
        TextView boxtext = findViewById(R.id.box_answer);
        boxtext.getLocationOnScreen(boxloc);

        int offsetx = boxloc[0] - imgloc[0];
        int offsety = boxloc[1] - imgloc[1];

        int fi = Integer.parseInt(currcolor.substring(1,3), 16);
        int se = Integer.parseInt(currcolor.substring(3,5), 16);
        int th = Integer.parseInt(currcolor.substring(5, 7), 16);

        for (int y = 0; y < boxtext.getHeight(); y++) {
            for (int x = 0; x < boxtext.getWidth(); x++) {
                int pv = imagea.getPixel(x + offsetx, y + offsety);
                imagea.setPixel(x+offsetx, y+offsety, Color.RED);


                short red = (short) ((pv >> 16) & 0xFF);
                short green = (short) ((pv >> 8) & 0xFF);
                short blue = (short) ((pv) & 0xFF);

                if ( ((fi - level < red) && (fi + level > red)) &&
                        ((se - level < green) && (se + level > green)) &&
                            ((th - level < blue) && (th + level > blue)) ){

                    Log.d(TAG, "GACHA");

                    Log.d(TAG, Integer.toString(offsetx+x) + "-" + Integer.toString(offsety+y)+ " = "
                            + Integer.toString(fi)+Integer.toString(se)+Integer.toString(th) + " - "
                            + Integer.toString(red)+Integer.toString(green)+Integer.toString(blue));
                    updateStats(true);
                    return;
                }
            }
        }
        updateStats(false);
    }

    private void updateStats(boolean answer){
        Toast.makeText(GameActivity.this,"Your answer is: " + answer, Toast.LENGTH_SHORT).show();

        if (answer) {
            currscore++;
            if (currscore > currhighscore){
                currhighscore = currscore;
            }
        } else {
            currlives--;
        }
    }

    private void logme(String y, int x){
        Log.d(TAG, y +": " + Integer.toString(x));
    }



    private void createCameraPreview() {
        try{
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert  texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(),imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if(cameraDevice == null)
                        return;
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(GameActivity.this, "Changed", Toast.LENGTH_SHORT).show();
                }
            },null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        if(cameraDevice == null)
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
        try{
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(),null,mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void openCamera() {
        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try{
            String cameraId = null;
            if (manager != null) {
                cameraId = manager.getCameraIdList()[0];
            }
            CameraCharacteristics characteristics = null;
            if (cameraId != null) {
                characteristics = manager.getCameraCharacteristics(cameraId);
            }
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            //Check realtime permission if run higher API 23
            if(ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            {
                ActivityCompat.requestPermissions(this,new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },REQUEST_CAMERA_PERMISSION);
                return;
            }
            manager.openCamera(cameraId,stateCallback,null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == REQUEST_CAMERA_PERMISSION)
        {
            if(grantResults[0] != PackageManager.PERMISSION_GRANTED)
            {
                Toast.makeText(this, "You can't use camera without permission", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if(textureView.isAvailable())
            openCamera();
        else
            textureView.setSurfaceTextureListener(textureListener);
    }

    @Override
    protected void onPause() {
        stopBackgroundThread();
        super.onPause();
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try{
            mBackgroundThread.join();
            mBackgroundThread= null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
}