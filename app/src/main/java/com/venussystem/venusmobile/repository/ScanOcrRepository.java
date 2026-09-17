package com.venussystem.venusmobile.repository;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;

public class ScanOcrRepository {

    public interface Callback {
        void onSuccess(@NonNull Text result);
        void onError(@NonNull Exception exception);
    }

    private final TextRecognizer recognizer;

    public ScanOcrRepository() {
        recognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
        );
    }

    public void recognize(
            @NonNull Context context,
            @NonNull Uri imageUri,
            @NonNull Callback callback
    ) {
        try {
            InputImage image = InputImage.fromFilePath(
                    context,
                    imageUri
            );

            Task<Text> task = recognizer.process(image);

            task.addOnSuccessListener(callback::onSuccess);

            task.addOnFailureListener(callback::onError);

        } catch (IOException e) {
            callback.onError(e);
        }
    }

    public void close() {
        recognizer.close();
    }
}