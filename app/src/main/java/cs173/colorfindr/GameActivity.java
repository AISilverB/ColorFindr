package cs173.colorfindr;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
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
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class GameActivity extends AppCompatActivity {

    private Thread runnable;
    private String TAG = "GameActivity";
    private TextureView textureView;
    private String ColorList[];
    private int currctr;
    private String currcolor;
    private int currscore;
    private int currlives;
    private int currhighscore;
    private boolean change;

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

    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreview();
            Log.d(TAG, "whut");
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
        Log.d(TAG, "ONCREATE");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        textureView = findViewById(R.id.textureView);


        if (textureView != null){
            textureView.setSurfaceTextureListener(textureListener);
            Log.d(TAG, "why not null: ");
        } else {
            Log.d(TAG, "why null: ");

        }

        ImageButton btnCapture = findViewById(R.id.btnCapture);
        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePicture();
            }
        });


        init();
    }

    private void init() {
        level = 50;
        currscore = 0;
        currlives = 3;


        String line = "";
        FileInputStream fis = null;
        try {
            File file = new File(this.getFilesDir(),"cfg");
            File gpxfile = new File(file, "gameconfig.txt");
            FileReader reader = new FileReader(gpxfile);
            if(file.exists())   Log.d(TAG, "MERONNNNNN NA");
            fis = new FileInputStream(gpxfile);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fis));
//            while ((line = bufferedReader.readLine()) != null) {
//                Log.d(TAG, "linemo: " + line);
//            }
            line = bufferedReader.readLine();

        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            currhighscore = Integer.parseInt(line);
            Log.d(TAG, "save naman eh :(");
        } catch(Exception e) {
            e.printStackTrace();
            currhighscore = 0;
        }
        currctr = -1;
        retrieveColorList();
        change = true;
        updateViews();
    }

    private void retrieveColorList(){
       ColorList = new String[127];
       ColorList = getResources().getStringArray(R.array.colorlist);
       currctr = 0;
//       UnlockedColors = new String[127];
    }

    private void takePicture() {
        try {
            if (cameraDevice == null)
                return;
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            try {
                CameraCharacteristics characteristics = null;
                if (manager != null) {
                    characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
                }
                Size[] jpegSizes = null;
                if (characteristics != null)
                    jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                            .getOutputSizes(ImageFormat.JPEG);

                TextureView imagex = findViewById(R.id.textureView);

                //Capture image with custom size
                Log.d(TAG, "BUHAY: " + runnable.isAlive());
                logme("textw", imagex.getWidth());
                logme("texth", imagex.getHeight());
                int width = imagex.getWidth();
                int height = imagex.getHeight();

                final ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
                List<Surface> outputSurface = new ArrayList<>(2);
                outputSurface.add(reader.getSurface());
                outputSurface.add(new Surface(textureView.getSurfaceTexture()));

                final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                captureBuilder.addTarget(reader.getSurface());
                captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

                //Check orientation base on device
                int rotation = getWindowManager().getDefaultDisplay().getRotation();
                captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

                ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader imageReader) {
                        Image image = null;
                        try {
                            image = reader.acquireLatestImage();

                            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                            byte[] bytes = new byte[buffer.capacity()];
                            buffer.get(bytes);

                            Bitmap bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
                            Matrix m = new Matrix();
                            m.postRotate(90);
                            Bitmap bmp = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
                            imageAnswer(bmp);

                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            {
                                if (image != null)
                                    image.close();
                            }
                        }
                    }
                };

                reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
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
                        try {
                            cameraCaptureSession.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                    }
                }, mBackgroundHandler);


            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } catch (Exception e){
            e.printStackTrace();
        }

    }

    private void updateViews (){
        runnable = new Thread() {
            @Override
            public void run() {
                while(true) {
//                    Log.d(TAG, "I AM A THREAD: " + change);
                    if (change) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                changeColor();
                            }
                        });
                        change = false;
                    }

                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();

                        Log.d(TAG, "I AM A DIE THREAD: " + change);
                    }
                }
            }
        };

        runnable.start();
    }

    @SuppressLint("ResourceType")
    private void changeColor() {

        Random rand = new Random();

        int randomColor = rand.nextInt(ColorList.length);

        currctr++;
        EditText edit = findViewById(R.id.curr_color_disp);
        GradientDrawable gradientDrawable = (GradientDrawable) edit.getBackground().mutate();


        String colval = ColorList[randomColor].replaceAll("[^A-Za-z0-9]", "");
        Log.d(TAG, colval);
        int objid = this.getResources().getIdentifier(colval, "color", this.getPackageName());
        gradientDrawable.setColor(ContextCompat.getColor(this, objid));

        TextView boxtext = findViewById(R.id.curr_color_name);
        boxtext.setText(ColorList[randomColor]);
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
        if (answer)
            Toast.makeText(GameActivity.this,"COLOR FOUND!", Toast.LENGTH_SHORT).show();
        else
            Toast.makeText(GameActivity.this,"Error 404: Color NOT FOUND!", Toast.LENGTH_SHORT).show();

        if (answer) {
            currscore++;
            if (currscore > currhighscore){
                currhighscore = currscore;
            }
        } else {
            currlives--;
            if (currlives == 0){
                savehighscore();
            }
        }

        change = true;
    }

    private void savehighscore() {
        File file = new File(this.getFilesDir(),"cfg");
        if(!file.exists()){
            file.mkdir();
        }

        try{
            File gpxfile = new File(file, "gameconfig.txt");
            FileWriter writer = new FileWriter(gpxfile);
            writer.write(Integer.toString(currhighscore));
            writer.flush();
            writer.close();
            Log.d(TAG, "FILE SAVEDDDD");
        }catch (Exception e){
            e.printStackTrace();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("OUT OF LIVES!")
                .setPositiveButton("OK", new DialogInterface.OnClickListener(){
                    public void onClick(DialogInterface arg0, int arg1) {
                        finish();
                    }
                })
        .setNegativeButton("NOT OK", new DialogInterface.OnClickListener(){
            public void onClick(DialogInterface arg0, int arg1) {
                finish();
            }
        });
        builder.show();
    }


    private void logme(String y, int x){
        Log.d(TAG, y +": " + Integer.toString(x));
    }



    private void createCameraPreview() {
        try{
            Log.d(TAG, "CREATECAMERAPREVIEW");
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert  texture != null;
            Log.d(TAG, "Texture is not null");

            texture.setDefaultBufferSize(imageDimension.getWidth(),imageDimension.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            captureRequestBuilder.addTarget(surface);

            Log.d(TAG, "Target added");

            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if(cameraDevice == null) {
                        Log.d(TAG, "Camera device is null");
                        return;
                    }
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(GameActivity.this, "Changed", Toast.LENGTH_SHORT).show();
                }
            },null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        Log.d(TAG, "UPDATEPREVIEW");

        if(cameraDevice == null)
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
        try{
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(),null,mBackgroundHandler);
        } catch (Exception e) {
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
            Log.d(TAG, "MANAGER NOT NULL");
            CameraCharacteristics characteristics = null;
            if (cameraId != null) {
                characteristics = manager.getCameraCharacteristics(cameraId);
            }
            Log.d(TAG, "CAMERA ID NOT NULL");
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;

            Log.d(TAG, "MAP NOT NULL");
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            //Check realtime permission if run higher API 23
            if(ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            {
                ActivityCompat.requestPermissions(this,new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },REQUEST_CAMERA_PERMISSION);

                Log.d(TAG, "DONE X PERMISSIONS");
                return;
            }
            manager.openCamera(cameraId,stateCallback,null);

            Log.d(TAG, "MANAGER OPEN CAMERA ");
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }



    private TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            Log.d(TAG, "Available surface");
            openCamera();
            Log.d(TAG, "done camera");
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            Log.d(TAG, "Surface Destroyed");
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
        Log.d(TAG, "resuming");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraDevice != null) {
            cameraDevice.close();
        }
        stopBackgroundThread();
    }

    private void stopBackgroundThread() {
        try{
            mBackgroundThread.quitSafely();
            mBackgroundThread.join();
            mBackgroundThread= null;
            mBackgroundHandler = null;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        Log.d(TAG, "THREADS RUNNING");
    }

}