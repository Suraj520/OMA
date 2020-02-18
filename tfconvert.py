import tensorflow as tf
import numpy as np

from tensorflow.python.platform import gfile

import cv2 as cv2


#DATASET_PATH = "/home/luca/Scrivania/tirocinio/optimizations/dataset/kitti2/"
DATASET_PATH = "/home/luca/Scrivania/tirocinio/optimizations/dataset/generated/15022020_111357/images/"

CROP_RES = (640,448)

GRAPH_PB_PATH = "/home/luca/Scrivania/tirocinio/mobilePydnet/Android/app/src/main/assets/optimized_pydnet++.pb"
GRAPH_TFLITE_PATH = "/home/luca/Scrivania/tirocinio/mobilePydnet/Android/app/src/main/assets/optimized_pydnet++.tflite"

#Funzione per creare il dataset rappresentativo.
#https://stackoverflow.com/questions/57877959/what-is-the-correct-way-to-create-representative-dataset-for-tfliteconverter
#Apro il video.
#Per ogni frame:
#crop cv2 e resize 

def rep_data_gen():
	a=[]
	for i in range(217):
		""" file_name = str(i).zfill(10) + ".png" """
		file_name = str(i) + ".png"
		img = cv2.imread(DATASET_PATH + file_name)
		img = cv2.resize(img, CROP_RES)
		img = img / 255.0
		img = img.astype(np.float32)
		a.append(img)
	a = np.array(a)
	print(a.shape)
	img = tf.data.Dataset.from_tensor_slices(a).batch(1)
	for i in img:
		#print(i)
		yield [i]

#-----------------------------------------------------------------------
#Convertitore da sessione

#Lettura file pd: nella sessione ho il graph.
session = tf.compat.v1.Session()
f = tf.io.gfile.GFile(GRAPH_PB_PATH,'rb')
graph_def = tf.compat.v1.GraphDef()
graph_def.ParseFromString(f.read())
session.graph.as_default()
g_in = tf.import_graph_def(graph_def, name="")

#graph_nodes=[n for n in graph_def.node]
#names = []
#for t in graph_nodes:
#	names.append(t.name)
	
#Ricavo i tensori di ingresso e uscita che serviranno al convertitore.

input_tensor = session.graph.get_tensor_by_name("im0:0")
input_tensor.set_shape([1,448,640,3])

out_tensor_half = session.graph.get_tensor_by_name("PSD/resize_images/ResizeBilinear:0")
#out_tensor_half.set_shape([1,448,640,1])

out_tensor_quarter = session.graph.get_tensor_by_name("PSD/resize_images_1/ResizeBilinear:0")
#out_tensor_quarter.set_shape([1,448,640,1])

out_tensor_height = session.graph.get_tensor_by_name("PSD/resize_images_2/ResizeBilinear:0")
#out_tensor_height.set_shape([1,448,640,1])
	
input_tensors = [input_tensor]
output_tensors = [out_tensor_height]


#converter = tf.compat.v1.lite.TFLiteConverter.from_session(session, input_tensors, output_tensors)

#-----------------------------------------------------------------------

#Convertitore da file

input_tensors = ["im0"]
output_tensors = ["PSD/resize_images_2/ResizeBilinear"]
input_shapes = {'im0': [1,448,640,3]}

converter = tf.compat.v1.lite.TFLiteConverter.from_frozen_graph(GRAPH_PB_PATH, input_tensors, output_tensors, input_shapes)

#Ottimizzazione Full Integer: Ha bisogno di un dataset rappresentativo.
#converter.optimizations = [tf.lite.Optimize.DEFAULT]
#converter.representative_dataset = rep_data_gen

#Operazione non supportata: 
#converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
#converter.inference_input_type = tf.int8
#converter.inference_output_type = tf.int8

#FLOAT16 optimization
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_types = [tf.float16]

#Conversione vera e propria
converter.experimental_new_converter = True
#converter.experimental_new_quantizer = True
tflite_model = converter.convert()
open("converted_model.tflite", "wb").write(tflite_model)