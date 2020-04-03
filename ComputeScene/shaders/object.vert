/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

uniform mat4 u_model;
uniform mat4 u_modelView;
uniform mat4 u_modelViewProjection;

attribute vec4 a_position;
attribute vec3 a_normal;
attribute vec2 a_texCoord;

varying vec2 v_texCoord;
varying vec3 v_worldPos;

void main() {
    //Ricavo le coordinate spaziali.
    //La matrice del model viene ricavata dall'ancora. Quindi teoricamente dovrebbe essere in scala con il resto delle distanze.
    v_worldPos = (u_model * a_position).xyz;
    v_texCoord = a_texCoord;
    gl_Position = u_modelViewProjection * a_position;
}
