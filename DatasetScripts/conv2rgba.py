import struct
from matplotlib import cm
import cv2

#https://stackoverflow.com/questions/14431170/get-the-bits-of-a-float-in-python
def floatToBitsToRGBA(f):
	s = struct.pack('>f', f)
	intValue = struct.unpack('>l', s)[0]
	rr = (intValue >> 24) & 0xFF
	gg = (intValue >> 16) & 0xFF
	bb = (intValue >> 8) & 0xFF
	aa = (intValue) & 0xFF
	
	return (rr, gg, bb, aa)
	
def floatToIntToRGBA(f, max):
	#Clipping e normalizzazione
	if f > max:
		f = max
	
	if f < 0.0:
		f = 0.0
		
	normalizedF = f / max
	
	intValue = int(round(normalizedF*255)) & 0xFF

	return (intValue, intValue, intValue, 255)
	
def floatToIntToPlasmaToRGBA(f, max):
	#Clipping e normalizzazione
	if f > max:
		f = max
	
	if f < 0.0:
		f = 0.0
		
	normalizedF = f / max

	colormap = cm.get_cmap('plasma')
	
	[rr, gg, bb, aa] = colormap(int(round(normalizedF*255)))
	
	rrInt = int(round(rr*255)) & 0xFF
	ggInt = int(round(gg*255)) & 0xFF
	bbInt = int(round(bb*255)) & 0xFF
	aaInt = int(round(aa*255)) & 0xFF
	
	return (rrInt, ggInt, bbInt, aaInt)

