from datetime import datetime

""" Definisce la Posa di un oggetto in un sistema di coordinate world-type """
class Pose:
	def __init__(self, name, tx, ty, tz, qx, qy, qz, qw):
		self.name = name
		
		"""Informazioni di traslazione"""
		
		self.tx = tx
		self.ty = ty
		self.tz = tz
		
		"""Informazioni di rotazione"""
		
		self.qx = qx
		self.qy = qy
		self.qz = qz
		self.qw = qw
		
	@classmethod
	def fromJSON(cls, name, jsonData):
		"""Inizializza l'oggetto Pose da un oggetto json"""
		return cls(name, jsonData["tx"],jsonData["ty"],jsonData["tz"],jsonData["qx"],jsonData["qy"],jsonData["qz"],jsonData["qw"])
		
	def toString(self):
		print("-------Posa------")
		print("Nome: "+self.name)
		print("Traslazione (x,y,z): "+str(self.tx)+", "+str(self.ty)+", "+str(self.tz))
		print("Rotazione (x,y,z,w): "+str(self.qx)+", "+str(self.qy)+", "+str(self.qz)+", "+str(self.qw))
		print("-----------------")

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


""" Oggetto che contiene i dati di un sensore """
"""
Collegamenti utili:
	1)Infomazioni su come ottenere dati dai sensori: https://developer.android.com/guide/topics/sensors/sensors_motion.html
	2)Classe utilizzata in android: https://developer.android.com/reference/android/hardware/SensorEvent
	3)Classe utilizzata in android: https://developer.android.com/reference/android/hardware/Sensor.html
"""
class SensorData:
	def __init__(self, sensorName, id, timestamp, accuracy, data):
		"""Nome del sensore con descrizione"""
		self.sensorName = sensorName
		"""ID del sensore (solitamente 0)"""
		self.id = id
		"""Timestamp in nanosecondi della misura del valore"""
		self.timestamp = timestamp
		"""Accuratezza della misura"""
		self.accuracy = accuracy
		"""Dati grezzi in virgola mobile."""
		self.data = data
		
	@classmethod
	def fromJSON(cls, jsonData):
		"""Inizializza l'oggetto SensorData da un oggetto json"""
		return cls(jsonData["sensorName"],jsonData["id"],jsonData["timestamp"],jsonData["accuracy"],jsonData["data"])
		
	def toString(self):
		print("-----Sensore-----")
		print("Nome: "+self.sensorName)
		print("ID: "+str(self.id))
		print("Timestamp (nanosec): "+str(self.timestamp))
		print("Accuratezza: "+str(self.accuracy))
		print("Dati grezzi: "+str(self.data))
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
class PointCloudDataset:
	def __init__(self, cameraPose, cameraDisplayPose, sensorPose, displayRotation, pointCloudTimestamp, visiblePoints, minDistance, maxDistance, origin, numPoints, numVisiblePoints, width, height, nearPlane, farPlane, viewmtx, projmtx, accelerometerData, linearAccelerometerData, gyroscopeData, pose6DofData):
		"""Posa reale della camera"""
		self.cameraPose = cameraPose
		"""Posa virtuale della camera, orientata secondo la rotazione del display"""
		self.cameraDisplayPose = cameraDisplayPose
		"""Posa del sensore fisico"""
		self.sensorPose = sensorPose		
		
		"""Rotazione del display"""
		""" 
			0 gradi: il telefono è in posizione naturale 'Dritto'.
			90 gradi: posizione landscape con la parte a destra sopra (secondo la posizione naturale).
			180 gradi: il telefono è sottosopra rispetto alla posizione naturale.
			270 gradi: posizione landscape con la parte a sinistra sopra (secondo la posizione naturale).
		"""
		self.displayRotation = displayRotation
		
		"""Timestamp in nanosecondi di quando è stata registrato il pointcloud"""
		"""Vengono salvati nel dataset solo i frame con timestamp del point cloud diverso"""
		self.pointCloudTimestamp = pointCloudTimestamp
		
		"""Array con i punti visibili dello schermo. Utilizzo la classe Point sopra"""
		"""I punti non visibili sono inizialmente sostituiti con dei None, poi filtrati dal metodo factory della classe"""
		self.visiblePoints = visiblePoints
		
		"""Distanza minima da un punto del pointcloud"""
		self.minDistance = minDistance
		"""Distanza massima da un punto del pointcloud"""
		self.maxDistance = maxDistance		
		
		"""Origine delle coordinate della finestra (XY), usata per i punti"""
		"""Non si riferisce alle coordinate TX, TY, TZ"""
		self.origin = origin
		
		"""Numero di punti registrati nella PointCloud"""
		self.numPoints = numPoints
		"""Numero di punti visibili sullo schermo"""
		"""L'array di prima contiene solo i punti visibili"""
		self.numVisiblePoints = numVisiblePoints
		
		"""Dimensioni del frame in pixel"""
		self.width = width
		self.height = height
		
		"""Roba di openGL"""
	
		"""I piani di proiezione servono per creare la matrice di proiezione"""
		"""Vengono usati per tagliare (clip) gli oggetti troppo vicini o troppo lontani"""
		
		self.nearPlane = nearPlane				
		self.farPlane = farPlane
		
		"""Matrici usate da opengl per varie trasformazioni grafiche"""
		"""Le ho incluse perché le coordinate XY le ho ricavate da queste"""
		"""//http://www.songho.ca/opengl/gl_transform.html"""
		
		self.viewmtx = viewmtx
		self.projmtx = projmtx
		
		"""Dati dai vari sensori"""
		"""I dati sono grezzi, lascio un link per le varie conversioni sopra"""
		"""Se un sensore non è presente, si utilizza None"""
		
		#Accelerometro
		self.accelerometerData = accelerometerData
		#Accelerometro lineare
		self.linearAccelerometerData = linearAccelerometerData		
		#Giroscopio
		self.gyroscopeData = gyroscopeData
		#Posa a 6 GdL
		self.pose6DofData = pose6DofData
	
	
	@classmethod
	def fromJSON(cls, jsonData):
		"""Inizializza l'oggetto PointCloudDataset da un oggetto json"""
		
		"""Inizializzo tutti gli oggetti"""
		cameraPose = Pose.fromJSON("Posa camera reale", jsonData["cameraPose"])
		cameraDisplayPose = Pose.fromJSON("Posa camera virtuale (display-oriented)", jsonData["cameraDisplayPose"])
		sensorPose = Pose.fromJSON("Posa del sensore", jsonData["sensorPose"])
		
		if "accelerometerData" in jsonData:
			accelerometerData = SensorData.fromJSON(jsonData["accelerometerData"])
		else:
			accelerometerData = None
			
		if "linearAccelerometerData" in jsonData:
			linearAccelerometerData = SensorData.fromJSON(jsonData["linearAccelerometerData"])
		else:
			linearAccelerometerData = None
			
		if "gyroscopeData" in jsonData:
			gyroscopeData = SensorData.fromJSON(jsonData["gyroscopeData"])
		else:
			gyroscopeData = None
			
		if "pose6DofData" in jsonData:
			pose6DofData = SensorData.fromJSON(jsonData["pose6DofData"])
		else:
			pose6DofData = None
				
		""" Filtro la lista dei punti visibili levando i None """
		""" Inoltre inizializzo gli oggetti Point """
		filteredPointList = []
		
		for point in jsonData["visiblePoints"]:
			if point != None:
				filteredPointList.append(Point.fromJSON(point))
				
				
		return cls(cameraPose, cameraDisplayPose, sensorPose, jsonData["displayRotation"], jsonData["pointCloudTimestamp"], filteredPointList, jsonData["minDistance"], jsonData["maxDistance"], jsonData["origin"], jsonData["numPoints"], jsonData["numVisiblePoints"], jsonData["width"], jsonData["height"], jsonData["nearPlane"], jsonData["farPlane"], jsonData["viewmtx"], jsonData["projmtx"], accelerometerData, linearAccelerometerData, gyroscopeData, pose6DofData)
		
	def toString(self):
		print("-----PointCloudDataset-----")
		print("Rotazione Display: "+str(self.displayRotation))
		print("Timestamp creazione PointCloud (nanosec): "+str(self.pointCloudTimestamp))
		print("Punti registrati: "+str(self.numPoints))
		print("Punti visibili: "+str(self.numVisiblePoints))
		print("Punto più vicino (m): "+str(self.minDistance))
		print("Punto più lontano (m): "+str(self.maxDistance))
		print("Origine coordinate XY: "+self.origin)
		print("Dimensione frame (WxH)(pixel): "+str(self.width)+"x"+str(self.height))
		
		print("-----Stampa dei punti------")
		
		for point in self.visiblePoints:
			point.toString()
		
		print("---------------------------")
		
		print("----Stampa dei sensori-----")
		
		if self.accelerometerData != None:
			self.accelerometerData.toString()
			
		if self.linearAccelerometerData != None:
			self.linearAccelerometerData.toString()
			
		if self.gyroscopeData != None:
			self.gyroscopeData.toString()
			
		if self.pose6DofData != None:
			self.pose6DofData.toString()
		
		print("---------------------------")
		
		print("---------------------------")