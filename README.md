# ObjectFinder
An android application that uses OpenCV and the findHomography function in order to detect a given object in a camera preview frame-by-frame.

## How to build
Open the Google Play store and search for "OpenCV Manager." Install it in order to download the necessary files.
Open the project in Android Studio and select build in order to create the APK.
Note: If you are having issues building the app, try using an older version of Android Studio.

## How to use
### Main UI (Video Feed)
Long-pressing anywhere on the feed will turn on/off the matching algorithm.
The "Select Frame" button uses the latest frame from the camera as a matching template, matching all further frames to that template.
The "Select Region" button is similar to the one above, however, it will launch a new activity to allow the user to choose which region of the frame to use as the template.

### Region Selection UI (Freeze Frame)
Tap on a corner of the region you want to track, then tap on the opposite corner (e.g. if you tap on the top-left corner of a box, tap on the bottom-right next). A blue box should appear indicating that the region is selected. If not, it will display an error message (minimum box size is 100x100). After you finish selecting both points, press Finish to go back to the original activity.

## Contributors
Created by Kenichi Yamamoto with guidance from Professor Hao Tang (Borough of Manhattan Community College)
