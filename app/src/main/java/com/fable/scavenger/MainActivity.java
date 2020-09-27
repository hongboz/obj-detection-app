package com.fable.scavenger;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.util.Size;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.common.model.LocalModel;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private Preview preview;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalyzer;
    private Camera camera;

    private File outputDirectory;
    private ExecutorService cameraExecutor;

    private static String TAG = "CameraXBasic";
    private static String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";
    private static int REQUEST_CODE_PERMISSIONS = 10;
    private String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

    private List<String> items = Arrays.asList("water bottle", "computer keyboard", "mouse", "racket", "backpack",
                                        "plastic bag", "wine bottle",
                                        "cup", "orange", "banana", "wallet",
                                        "notebook", "wall clock", "sunglasses",
                                        "vase");

    private TextView currentTarget;
    private TextView scoreView;
    private TextView timerView;
    String currentItem;
    int index = 0;

    int score = -200;

    private SoundPlayer soundPlayer;





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        currentItem = items.get(index);

        currentTarget = findViewById(R.id.currentTarget);
        scoreView = findViewById(R.id.scoreView);
        timerView = findViewById(R.id.timerView);

        soundPlayer = new SoundPlayer(this);

        updateTarget();

        new CountDownTimer(120000, 1000) {

            public void onTick(long millisUntilFinished) {
                timerView.setText("seconds remaining: " + millisUntilFinished / 1000);
                //here you can have your logic to set text to edittext
            }

            public void onFinish() {
                timerView.setText("done!");
            }

        }.start();

        if (allPermissionsGranted()){
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        // Setup the listener for skip button
        Button skip_button = findViewById(R.id.skip_button);
        skip_button.setOnClickListener(view -> {
            // skip target
            skipTarget();
        });

        outputDirectory = getOutputDirectory();

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void updateTarget() {
        score += 200;
        scoreView.setText("Score: " + String.valueOf(score));
        currentTarget.setText(items.get(index));
        currentItem = items.get(index);
    }

    private void skipTarget(){
        index++;
        currentTarget.setText(items.get(index));
        currentItem = items.get(index);
    }

    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(()-> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));

    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        PreviewView viewFinder = findViewById(R.id.viewFinder);
        preview.setSurfaceProvider(viewFinder.createSurfaceProvider());

        ImageAnalysis imageAnalyzer =
                new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

        imageAnalyzer.setAnalyzer(ContextCompat.getMainExecutor(this), new YourAnalyzer());

        cameraProvider.unbindAll();

        Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, preview, imageAnalyzer);
    }


    private File getOutputDirectory(){
        return getFilesDir();
    }

    private boolean allPermissionsGranted() {
        if (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
            return true;
        } else{return false;}
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(getApplicationContext(),
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

        private class YourAnalyzer implements ImageAnalysis.Analyzer {

        @Override
        public void analyze(ImageProxy imageProxy) {
            @SuppressLint("UnsafeExperimentalUsageError") Image mediaImage = imageProxy.getImage();
            if (mediaImage != null) {
                InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                // Pass image to an ML Kit Vision API
                LocalModel localModel =
                        new LocalModel.Builder()
                                .setAssetFilePath("mobilenet_v1_1.0_224_quantized_1_metadata_1.tflite")
                                .build();

//                ObjectDetectorOptions options =
//                        new ObjectDetectorOptions.Builder()
//                                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
//                                .enableClassification()  // Optional
//                                .build();

                CustomObjectDetectorOptions customObjectDetectorOptions =
                        new CustomObjectDetectorOptions.Builder(localModel)
                                .setDetectorMode(CustomObjectDetectorOptions.STREAM_MODE)
                                .enableMultipleObjects()
                                .enableClassification()
                                .setClassificationConfidenceThreshold(0.5f)
                                .setMaxPerObjectLabelCount(1)
                                .build();

                ObjectDetector objectDetector =
                        ObjectDetection.getClient(customObjectDetectorOptions);

                objectDetector
                        .process(image)
                        .addOnFailureListener(e -> {
                            Log.d("error","Oops, something went wrong!");
                            imageProxy.close();
                        })
                        .addOnSuccessListener(results -> {
                            for (DetectedObject detectedObject : results) {
                                for (DetectedObject.Label label : detectedObject.getLabels()) {
                                    String text = label.getText();
                                    int index = label.getIndex();
                                    float confidence = label.getConfidence();

                                    compareObjectToList(text, currentItem);
                                    if (Objects.equals(text, currentItem)){
                                        Log.i("confid", String.valueOf(confidence));
                                    }

                                }

                            }

                            imageProxy.close();
                        });
            }
        }
    }

    private void compareObjectToList(String text, String currentItem) {

        if (Objects.equals(text, currentItem)){
            // Add points to player and remove item from list
            Toast.makeText(getApplicationContext(),
                    (currentItem) + " Complete!",
                    Toast.LENGTH_SHORT).show();
            index++;
            updateTarget();
            soundPlayer.playDingSound();
        } else{
            Log.i("Match result:",currentItem + " Failed!");
        }
    }


}