#Default
./toco --input_file="/home/luca/Scrivania/OMA/optimizations/default/optimized_pydnet++.pb" \
--input_shapes="1,448,640,3" \
--output_file="/home/luca/Scrivania/OMA/optimizations/conv.tflite" \
--output_arrays="PSD/resize_images/ResizeBilinear,PSD/resize_images_1/ResizeBilinear,PSD/resize_images_2/ResizeBilinear" \
--input_arrays="im0" \

#Quantized: BROKEN
./toco --input_file="/home/luca/Scrivania/OMA/optimizations/default/optimized_pydnet++.pb" \
--input_shapes="1,448,640,3" \
--output_file="/home/luca/Scrivania/OMA/optimizations/conv.tflite" \
--output_arrays="PSD/resize_images_2/ResizeBilinear" \
--input_arrays="im0" \
--inference_type="QUANTIZED_UINT8" \
--default_ranges_min=0.0 \
--default_ranges_max=10.0 \
--std_values="255.0"

#Float16
./toco --input_file="/home/luca/Scrivania/OMA/optimizations/default/optimized_pydnet++.pb" \
--output_file="/home/luca/Scrivania/OMA/optimizations/conv.tflite" \
--output_arrays="PSD/resize_images_2/ResizeBilinear" \
--input_shapes="1,448,640,3" \
--input_arrays="im0" \
--quantize_to_float16=true \
 --post_training_quantize=true

./toco --input_file="/home/luca/Scrivania/OMA/optimizations/converted_model.tflite" \
--input_format="TFLITE" \
--input_shapes="1,448,640,3" \
--output_file="/home/luca/Scrivania/OMA/optimizations/conv.tflite" \
--output_arrays="PSD/resize_images_2/ResizeBilinear" \
--input_arrays="im0" \
