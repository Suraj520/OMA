#!/usr/bin/env python

import sys
import json
from os import listdir
from os.path import isfile, join
from TOFDataset import *
from PIL import Image
from conv2rgba import *

if len(sys.argv) < 8:
	print("Usage: "+sys.argv[0]+" datasetPath conversionMethod pointRadius R G B A")
	print("Conversion methods:")
	print("1) Float IEEE-754 -> RGBA")
	print("2) Float IEEE-754 -> int[0,255] -> RGBA")
	print("3) Float IEEE-754 -> int[0,255] -> plasma -> RGBA")
	print("Default conversion method: 3")
	print("Default point size: 1")
	print("R G B A: background color")
	print("Default RGBA background color: 0 0 0 0")
	sys.exit(1)
	
datasetPath = sys.argv[1]
conversionMethod = int(sys.argv[2])
pointRadius = int(sys.argv[3])

rr = int(sys.argv[4])
gg = int(sys.argv[5])
bb = int(sys.argv[6])
aa = int(sys.argv[7])

#Check sul raggio: minimo 0 (un solo pixel)
if pointRadius < 0:
	pointRadius = 1
	
if rr < 0 or rr > 255:
	rr = 0
	
if gg < 0 or gg > 255:
	gg = 0
	
if bb < 0 or bb > 255:
	bb = 0
	
if aa < 0 or aa > 255:
	aa = 0
	

#https://stackoverflow.com/questions/3207219/how-do-i-list-all-files-of-a-directory
pointsFiles = [f for f in listdir(datasetPath) if isfile(join(datasetPath, f))]

for pointFile in pointsFiles:
	#Verifico l'estensione json
	if pointFile.endswith(".json"):
		#https://stackabuse.com/reading-and-writing-json-to-a-file-in-python/
		with open(join(datasetPath, pointFile)) as json_file:
			mapFile = pointFile.replace(".json", ".png")
		
			print("Converting file: " + pointFile)
			print("Into: " + mapFile)
			data = json.load(json_file)
			dataset = TOFDataset.fromJSON(data)
			
			#Uso il metodo integrato per la conversione in png.
			#https://stackoverflow.com/questions/1038550/in-python-how-do-i-easily-generate-an-image-file-from-some-source-data
			img = Image.new('RGBA', (dataset.width, dataset.height), (rr, gg, bb, aa))
			
			for point in dataset.points:
			
				if conversionMethod == 1:
					data = floatToBitsToRGBA(point.distance)
				elif conversionMethod == 2:
					data = floatToIntToRGBA(point.distance, dataset.maxDistance)
				else:
					data = floatToIntToPlasmaToRGBA(point.distance, dataset.maxDistance)
			
				#https://stackoverflow.com/questions/36468530/changing-pixel-color-value-in-pil
				for x in range(point.x-pointRadius, point.x+pointRadius+1):
					for y in range(point.y-pointRadius, point.y+pointRadius+1):
						if 0 <= x and x < dataset.width and 0 <= y and y < dataset.height:
							img.putpixel((x, y), data)
			
			img.save(join(datasetPath, mapFile))