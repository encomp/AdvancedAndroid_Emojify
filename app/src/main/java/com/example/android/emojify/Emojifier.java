/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.emojify;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;

import java.io.IOException;
import java.util.List;

class Emojifier {

  private static final String LOG_TAG = Emojifier.class.getSimpleName();
  private static final double SMILING_PROB_THRESHOLD = .15;
  private static final double EYE_OPEN_PROB_THRESHOLD = .5;
  private static final float EMOJI_SCALE_FACTOR = .9f;

  interface onFaceDetectedListener {
    void onFaceDetected(Bitmap bitmap);
  }

  /**
   * Method for detecting faces in a bitmap.
   *
   * @param context The application context.
   * @param picture The picture in which to detect the faces.
   */
  static void detectFacesAndOverlayEmoji(
      onFaceDetectedListener onFaceDetectedListener, final Context context, final Bitmap picture) {

    FirebaseApp.initializeApp(context);
    FirebaseVisionFaceDetectorOptions options =
        new FirebaseVisionFaceDetectorOptions.Builder()
            .setPerformanceMode(FirebaseVisionFaceDetectorOptions.FAST)
            .setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS)
            .build();

    // Create the face detector, disable tracking and enable classifications
    final FirebaseVisionFaceDetector detector =
        FirebaseVision.getInstance().getVisionFaceDetector(options);

    // Detect the faces
    Task<List<FirebaseVisionFace>> faces =
        detector
            .detectInImage(FirebaseVisionImage.fromBitmap(picture))
            .addOnSuccessListener(
                new FaceRender(context, picture, onFaceDetectedListener, detector))
            .addOnFailureListener(
                new OnFailureListener() {
                  @Override
                  public void onFailure(@NonNull Exception e) {
                    // Task failed with an exception
                    Toast.makeText(
                            context, "detectFacesAndOverlayEmoji failed...", Toast.LENGTH_SHORT)
                        .show();
                  }
                });
  }

  /**
   * Determines the closest emoji to the expression on the face, based on the odds that the person
   * is smiling and has each eye open.
   *
   * @param face The face for which you pick an emoji.
   */
  private static Emoji whichEmoji(FirebaseVisionFace face) {

    // Log all the probabilities
    Log.d(LOG_TAG, "whichEmoji: smilingProb = " + face.getSmilingProbability());
    Log.d(LOG_TAG, "whichEmoji: leftEyeOpenProb = " + face.getLeftEyeOpenProbability());
    Log.d(LOG_TAG, "whichEmoji: rightEyeOpenProb = " + face.getRightEyeOpenProbability());

    boolean smiling = face.getSmilingProbability() > SMILING_PROB_THRESHOLD;
    boolean leftEyeClosed = face.getLeftEyeOpenProbability() < EYE_OPEN_PROB_THRESHOLD;
    boolean rightEyeClosed = face.getRightEyeOpenProbability() < EYE_OPEN_PROB_THRESHOLD;

    // Determine and log the appropriate emoji
    Emoji emoji;
    if (smiling) {
      if (leftEyeClosed && !rightEyeClosed) {
        emoji = Emoji.LEFT_WINK;
      } else if (rightEyeClosed && !leftEyeClosed) {
        emoji = Emoji.RIGHT_WINK;
      } else if (leftEyeClosed) {
        emoji = Emoji.CLOSED_EYE_SMILE;
      } else {
        emoji = Emoji.SMILE;
      }
    } else {
      if (leftEyeClosed && !rightEyeClosed) {
        emoji = Emoji.LEFT_WINK_FROWN;
      } else if (rightEyeClosed && !leftEyeClosed) {
        emoji = Emoji.RIGHT_WINK_FROWN;
      } else if (leftEyeClosed) {
        emoji = Emoji.CLOSED_EYE_FROWN;
      } else {
        emoji = Emoji.FROWN;
      }
    }

    // Log the chosen Emoji
    Log.d(LOG_TAG, "whichEmoji: " + emoji.name());

    return emoji;
  }

  private static Bitmap addBitmapToFace(
      final Bitmap backgroundBitmap, Bitmap emojiBitmap, final FirebaseVisionFace face) {

    // Scale the emoji so it looks better on the face
    float scaleFactor = EMOJI_SCALE_FACTOR;

    // Determine the size of the emoji to match the width of the face and preserve aspect ratio
    int newEmojiWidth = (int) (face.getBoundingBox().width() * scaleFactor);
    int newEmojiHeight =
        (int) (emojiBitmap.getHeight() * newEmojiWidth / emojiBitmap.getWidth() * scaleFactor);

    // Scale the emoji
    emojiBitmap = Bitmap.createScaledBitmap(emojiBitmap, newEmojiWidth, newEmojiHeight, false);

    // Determine the emoji position so it best lines up with the face
    float emojiPositionX =
        (face.getBoundingBox().right + face.getBoundingBox().width() / 2)
            - emojiBitmap.getWidth() / 2;
    float emojiPositionY =
        (face.getBoundingBox().centerY() + face.getBoundingBox().height() / 2)
            - emojiBitmap.getHeight() / 3;

    // Initialize the results bitmap to be a mutable copy of the original image
    Bitmap resultBitmap =
        Bitmap.createBitmap(
            backgroundBitmap.getWidth(),
            backgroundBitmap.getHeight(),
            backgroundBitmap.getConfig());
    // Create the canvas and draw the bitmaps to it
    Canvas canvas = new Canvas(resultBitmap);
    canvas.drawBitmap(backgroundBitmap, 0, 0, null);
    canvas.drawBitmap(emojiBitmap, emojiPositionX, emojiPositionY, null);

    return resultBitmap;
  }

  // Enum for all possible Emojis
  private enum Emoji {
    SMILE,
    FROWN,
    LEFT_WINK,
    RIGHT_WINK,
    LEFT_WINK_FROWN,
    RIGHT_WINK_FROWN,
    CLOSED_EYE_SMILE,
    CLOSED_EYE_FROWN
  }

  private static final class FaceRender implements OnSuccessListener<List<FirebaseVisionFace>> {

    private final Context context;
    private final Bitmap picture;
    private final onFaceDetectedListener onFaceDetectedListener;
    private final FirebaseVisionFaceDetector detector;

    private FaceRender(
        final Context context,
        final Bitmap picture,
        final onFaceDetectedListener onFaceDetectedListener,
        final FirebaseVisionFaceDetector detector) {
      this.context = context;
      this.picture = picture;
      this.onFaceDetectedListener = onFaceDetectedListener;
      this.detector = detector;
    }

    @Override
    public void onSuccess(List<FirebaseVisionFace> faces) {
      Log.i(LOG_TAG, "Faces found: " + faces.size());
      if (faces.size() == 0) {
        Toast.makeText(context, R.string.no_faces_message, Toast.LENGTH_SHORT).show();
      } else {
        Bitmap resultBitmap = picture;
        for (int i = 0; i < faces.size(); ++i) {
          FirebaseVisionFace face = faces.get(i);
          Bitmap emojiBitmap = null;
          switch (whichEmoji(face)) {
            case SMILE:
              emojiBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.smile);
              break;
            case FROWN:
              emojiBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.frown);
              break;
            case LEFT_WINK:
              emojiBitmap =
                  BitmapFactory.decodeResource(context.getResources(), R.drawable.leftwink);
              break;
            case RIGHT_WINK:
              emojiBitmap =
                  BitmapFactory.decodeResource(context.getResources(), R.drawable.rightwink);
              break;
            case LEFT_WINK_FROWN:
              emojiBitmap =
                  BitmapFactory.decodeResource(context.getResources(), R.drawable.leftwinkfrown);
              break;
            case RIGHT_WINK_FROWN:
              emojiBitmap =
                  BitmapFactory.decodeResource(context.getResources(), R.drawable.rightwinkfrown);
              break;
            case CLOSED_EYE_SMILE:
              emojiBitmap =
                  BitmapFactory.decodeResource(context.getResources(), R.drawable.closed_smile);
              break;
            case CLOSED_EYE_FROWN:
              emojiBitmap =
                  BitmapFactory.decodeResource(context.getResources(), R.drawable.closed_frown);
              break;
            default:
              emojiBitmap = null;
              Toast.makeText(context, R.string.no_emoji, Toast.LENGTH_SHORT).show();
          }
          resultBitmap = addBitmapToFace(resultBitmap, emojiBitmap, face);
        }
        onFaceDetectedListener.onFaceDetected(resultBitmap);
      }
      try {
        detector.close();
      } catch (IOException e) {
      }
    }
  }
}
