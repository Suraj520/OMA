#!/usr/bin/env python

import sys
import json
from PointCloudDataset import *

"""Richiedo all'utente il path del file json"""
#print("Inserisci il path del file JSON:")
#path = input()

if len(sys.argv) < 2:
	print("Usage: "+sys.argv[0]+" pointsPath")
	sys.exit(1)

path = sys.argv[1]

with open(path) as json_file:
	data = json.load(json_file)
	dataset = PointCloudDataset.fromJSON(data)
	dataset.toString()