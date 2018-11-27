package cs173.colorfindr;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

public class GameActivity extends AppCompatActivity {

    private String TAG = "GameActivity";
    private ImageButton btnCapture;
    private TextureView textureView;
    private int intArray[];
    private int UnlockedColors[];
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

    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSessions;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private int level;
    //Save to FILE
    private File file;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private Boolean answer;

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
            cameraDevice=null;
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        init();

        textureView = (TextureView)findViewById(R.id.textureView);
        //From Java 1.4 , you can use keyword 'assert' to check expression true or false
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);
        btnCapture = (ImageButton)findViewById(R.id.btnCapture);
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

        retrieveColorList();
        changeColor(currctr);
    }

    private void retrieveColorList(){
       ColorList = new String[127];
       ColorList = getResources().getStringArray(R.array.colorlist);
       currctr = 0;
    }
    private void takePicture() {
        if(cameraDevice == null)
            return;
        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try{
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if(characteristics != null)
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        .getOutputSizes(ImageFormat.JPEG);

            TextureView imagex = (TextureView) findViewById(R.id.textureView);

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

            // file = new File(Environment.getExternalStorageDirectory()+"/"+UUID.randomUUID().toString()+".jpg");
            file = new File(Environment.getExternalStorageDirectory()+"/img/"+Calendar.getInstance().getTime().toString()+".jpg");

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

//                        Log.d(TAG, Integer.toString(bmp.getWidth())+ " w-h " + Integer.toString(bmp.getHeight()));

                        imageAnswer(bmp);
                        ////
//                        // bitmap to save
//                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
//                        bmp.compress(Bitmap.CompressFormat.JPEG, 100, bos);
//                        bytes = bos.toByteArray();
//
//                        save(bytes);
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

//                 NOT TO SAVE THE FILE
                private void save(byte[] bytes) throws IOException {
                    OutputStream outputStream = null;
                    try{
                        outputStream = new FileOutputStream(file);
                        outputStream.write(bytes);
                        Log.d(TAG, "file saved");
                    }finally {
                        if(outputStream != null)
                            outputStream.close();
                    }
                }
            };

            reader.setOnImageAvailableListener(readerListener,mBackgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    //Toast.makeText(GameActivity.this, "Saved "+file, Toast.LENGTH_SHORT).show();
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

        currctr++;
        changeColor(currctr);
    }

    @SuppressLint("ResourceType")
    private void changeColor(int currctr) {
        String colval = ColorList[currctr].replaceAll("[^A-Za-z0-9]", "");
        EditText edit = (EditText) findViewById(R.id.curr_color_disp);
        GradientDrawable gradientDrawable = (GradientDrawable) edit.getBackground().mutate();
        Log.d(TAG, colval);

        int objid = this.getResources().getIdentifier(colval, "color", this.getPackageName());
        gradientDrawable.setColor(ContextCompat.getColor(this, objid));

        TextView boxtext = (TextView) findViewById(R.id.curr_color_name);
        boxtext.setText(ColorList[currctr]);
        currcolor = getResources().getString(objid).substring(2);

        TextView scoretext = (TextView) findViewById(R.id.curr_score);
        scoretext.setText(Integer.toString(currscore));
        TextView livestext = (TextView) findViewById(R.id.curr_lives);
        livestext.setText(Integer.toString(currlives));
        TextView highscoretext = (TextView) findViewById(R.id.curr_high_score);
        highscoretext.setText(Integer.toString(currhighscore));
    }

    private void imageAnswer(Bitmap imagea) {
        int[] imgloc = new int[2];
        TextureView imagex = (TextureView) findViewById(R.id.textureView);
        imagex.getLocationOnScreen(imgloc);

        int[] boxloc = new int[2];
        TextView boxtext = (TextView) findViewById(R.id.box_answer);
        boxtext.getLocationOnScreen(boxloc);

        int offsetx = boxloc[0] - imgloc[0];
        int offsety = boxloc[1] - imgloc[1];

        int fi = Integer.parseInt(currcolor.substring(1,3), 16);
        int se = Integer.parseInt(currcolor.substring(3,5), 16);
        int th = Integer.parseInt(currcolor.substring(5, 7), 16);


        int off = level;
        for (int y = 0; y < boxtext.getHeight(); y++) {
            for (int x = 0; x < boxtext.getWidth(); x++) {
                int pv = imagea.getPixel(x + offsetx, y + offsety);
                imagea.setPixel(x+offsetx, y+offsety, Color.RED);



                short red = (short) ((pv >> 16) & 0xFF);
                short green = (short) ((pv >> 8) & 0xFF);
                short blue = (short) ((pv >> 0) & 0xFF);


                if ( ((fi - off < red) && (fi + off > red)) &&
                        ((se - off < green) && (se + off > green)) &&
                            ((th - off < blue) && (th + off > blue)) ){

                    Log.d(TAG, "GACHA");

                    Log.d(TAG, Integer.toString(offsetx+x) + "-" + Integer.toString(offsety+y)+ " = "
                            + Integer.toString(fi)+Integer.toString(se)+Integer.toString(th) + " - "
                            + Integer.toString(red)+Integer.toString(green)+Integer.toString(blue));
                    updateStats(true);
                    return;
// return imagea;
                }

//                Log.d(TAG, Integer.toString(offsetx+x) + "-" + Integer.toString(offsety+y)
//                        + currcolor.substring(1,2) + " = "
//                        + Integer.toString(fi)+Integer.toString(se)+Integer.toString(th) + " - "
//                        + Integer.toString(red)+Integer.toString(green)+Integer.toString(blue));
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

    private void logmef(String y, float x){
        Log.d(TAG, y +": " + Float.toString(x));
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
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
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