# OMA

Occlusion Mask on Android

## Getting Started

Clone this repository and use Android Studio to build **ARPydnet** and **Dataset Generator**.

**ARPydnet** is an AR demo app. It combines ARCore and monocular depth estimation to generate Occlusion Mask.
The main AR demo is from [Hello AR](https://github.com/google-ar/arcore-android-sdk/tree/master/samples/hello_ar_java) project.
The monocular depth estimation used is [Mobile PyDNet](https://github.com/FilippoAleotti/mobilePydnet/)

**Dataset Generator** is an app to generate useful dataset for (re-)training monocular depth estimation ANN.
It uses ARCore [PointCloud](https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/PointCloud) and the camera frames.

**Dataset Scripts** folder contains some python scripts to extract data from generated datasets.

### Prerequisites

**ARPydnet** and **Dataset Generator** have some prerequisites to run on your smartphone:

- Supported ARCore smartphone
- Android 8.1 (SDK 27) 
- ARCore Services 1.15.0

**Dataset Scripts** needs Python with some plugins:

- Pillow https://pillow.readthedocs.io/en/stable/
- OpenCV2 https://pypi.org/project/opencv-python/
- Matplotlib https://matplotlib.org/

### Run ARPydnet

Use Android Studio to generate an APK file or install directly on your developement smartphone.
The application needs some permissions to run on your smartphone.
Occlusion Mask is disabled by default. Use the top menu to enable it.
There is another menu button to show depth estimation.

### Run Dataset Generator

Use Android Studio to generate an APK file or install directly on your developement smartphone.
The application needs some permissions to run on your smartphone.
Use the buttom *Record* to generate a new dataset.
You can set a threshold value to trigger the saving of a new frame.

## Authors

* **Luca Bartolomei** - *Initial work* - [bartn8](https://github.com/bartn8)

See also the list of [contributors](https://github.com/your/project/contributors) who participated in this project.

## License

To Be Defined
