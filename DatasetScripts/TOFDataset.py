from datetime import datetime

""" Definisce un punto sia in un sistema di coordinate world-type sia nel sistema xy della finestra """
class Point:
	def __init__(self, x,y,tx,ty,tz,confidence,distance):
	
		""" Coordinate rispetto alla finestra"""
		""" Sono rispetto ad un origine """
		self.x = x
		self.y = y
		
		""" Coordinate di traslazione """
		""" Sono nello stesso sistema della posa della camera """
		self.tx = tx
		self.ty = ty
		self.tz = tz
		
		
		""" ARCore calcola un fattore di confidenza del punto da 0.0 a 1.0 """
		self.confidence = confidence
		
		""" Ho già calcolato la distanza del punto dalla posa della camera """
		""" Ho utilizzato la posa virtuale, non credo che cambi rispetto alla posa reale, dato che impone solo una rotazione"""
		self.distance = distance

	@classmethod
	def fromJSON(cls, jsonData):
		"""Inizializza l'oggetto Point da un oggetto json"""
		return cls(jsonData["x"],jsonData["y"],jsonData["tx"],jsonData["ty"],jsonData["tz"],jsonData["confidence"],jsonData["distance"])
		
	def toString(self):
		print("------Punto------")
		print("Coordinate XY: "+str(self.x)+", "+str(self.y))
		print("Traslazione (x,y,z): "+str(self.tx)+", "+str(self.ty)+", "+str(self.tz))
		print("Confidenza: "+str(self.confidence))
		print("Distanza: "+str(self.distance))
		print("-----------------")

""" Contiene tutti i dati di un dataset"""
"""
Collegamenti utili:
	1)Infomazioni sulla posa reale delle camera: https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Camera#getPose()
	2)Infomazioni sulla posa virtuale delle camera: https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Camera#getDisplayOrientedPose()
	3)Informazioni sulla posa del sensore: https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Frame#getAndroidSensorPose()
	4)Informazioni sul pointcloud: https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/PointCloud	
	5)Concetti di alto livello su ARCore: https://developers.google.com/ar/reference/c/group/concepts
"""
class TOFDataset:
	def __init__(self, origin, tofTimestamp, minDistance, maxDistance, width, height, points, numPoints, displayRotation):

		"""Origine delle coordinate della finestra (XY), usata per i punti"""
		"""Non si riferisce alle coordinate TX, TY, TZ"""
		self.origin = origin
		
		"""Timestamp in nanosecondi di quando è stata registrato"""
		"""Vengono salvati nel dataset solo i frame con timestamp diverso"""
		self.tofTimestamp = tofTimestamp
	
		"""Distanza minima da un punto del pointcloud"""
		self.minDistance = minDistance
		"""Distanza massima da un punto del pointcloud"""
		self.maxDistance = maxDistance	
	
		"""Dimensioni del frame in pixel"""
		self.width = width
		self.height = height
		
		"""Array con i punti dello schermo. Utilizzo la classe Point sopra"""
		self.points = points
	
		"""Rotazione del display"""
		""" 
			0 gradi: il telefono è in posizione naturale 'Dritto'.
			90 gradi: posizione landscape con la parte a destra sopra (secondo la posizione naturale).
			180 gradi: il telefono è sottosopra rispetto alla posizione naturale.
			270 gradi: posizione landscape con la parte a sinistra sopra (secondo la posizione naturale).
		"""
		self.displayRotation = displayRotation
			
		"""Numero di punti registrati nel dataset"""
		self.numPoints = numPoints	
	
	@classmethod
	def fromJSON(cls, jsonData):
		"""Inizializza l'oggetto TOFDataset da un oggetto json"""
			
		""" Filtro la lista dei punti levando i None """
		""" Inoltre inizializzo gli oggetti Point """
		filteredPointList = []
		
		for point in jsonData["points"]:
			if point != None:
				filteredPointList.append(Point.fromJSON(point))
				
				
		numPoints = len(filteredPointList)
				
		if "numPoints" in jsonData:
			numPoints = jsonData["numPoints"]
				
		return cls(jsonData["origin"], jsonData["tofTimestamp"], jsonData["minDistance"], jsonData["maxDistance"], jsonData["width"], jsonData["height"], filteredPointList, numPoints, jsonData["displayRotation"])
		
	def toString(self):
		print("-----TOFDataset-----")
		print("Rotazione Display: "+str(self.displayRotation))
		print("Timestamp creazione (nanosec): "+str(self.pointCloudTimestamp))
		print("Punti registrati: "+str(self.numPoints))
		print("Punto più vicino (m): "+str(self.minDistance))
		print("Punto più lontano (m): "+str(self.maxDistance))
		print("Origine coordinate XY: "+self.origin)
		print("Dimensione frame (WxH)(pixel): "+str(self.width)+"x"+str(self.height))
		
		print("-----Stampa dei punti------")
		
		for point in self.points:
			point.toString()
				
		print("---------------------------")