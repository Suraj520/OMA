#!/usr/bin/env p	ython

import functools
import sys
from pathlib import Path
from shutil import move

datasetPath = Path(sys.argv[1])

print("Directory: "+str(datasetPath))

if not datasetPath.is_dir():
	print("Directory non valida")
	sys.exit(1)
	
imagesPath = datasetPath / "images"
scenesPath = datasetPath / "points" 
pointsPath = datasetPath / "scenes" 

imagesPath = imagesPath.resolve()
scenesPath = scenesPath.resolve()
pointsPath = pointsPath.resolve()


def mySort(x,y):
	x = str(x)
	y = str(y)
	if len(x) != len(y):
		return len(y)-len(x)
	else:
		if x > y:
			return 1
		elif x < y: 
			return -1
		else:
			return 0
		
		

keyFunc = functools.cmp_to_key(mySort)


def myKey(x):
	x = str(x.name)
	return len(x) + int(x.split(".")[0])

if imagesPath.is_dir():
	imageCounter = 0
	imageList = []

	for image in imagesPath.iterdir():
		imageList.append(image)

	imageList.sort(key = myKey)
	
	for image in imageList:
		if int(image.name.split(".")[0]) != imageCounter:
			move(imagesPath / image.name, imagesPath / (str(imageCounter)+image.suffix))
		
		imageCounter = imageCounter + 1
	


if scenesPath.is_dir():
	sceneCounter = 0
	sceneList = []

	for scene in scenesPath.iterdir():
		sceneList.append(scene)

	sceneList.sort(key = myKey)
	
	for scene in sceneList:
		if int(scene.name.split(".")[0]) != sceneCounter:
			move(scenesPath / scene.name, scenesPath / (str(sceneCounter)+scene.suffix))
		
		sceneCounter = sceneCounter + 1
	
	

if pointsPath.is_dir():
	pointCounter = 0
	pointList = []

	for point in pointsPath.iterdir():
		pointList.append(point)

	pointList.sort(key = myKey)
	
	for point in pointList:
		if int(point.name.split(".")[0]) != pointCounter:
			move(pointsPath / point.name, pointsPath / (str(pointCounter)+point.suffix))
		
		pointCounter = pointCounter + 1
	




