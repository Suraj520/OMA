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
uniform mat4 u_modelViewProjection;
uniform mat2 u_planeUvMatrix;
uniform vec3 u_normal;

attribute vec3 a_positionXZAlpha; // (x, z, alpha)

varying vec3 v_texCoordAlpha;

void main() {
   //Il piano è situato in XZ... Ha senso per una terna destrorsa.
   vec4 local_pos = vec4(a_positionXZAlpha.x, 0.0, a_positionXZAlpha.y, 1.0);
   //La matrice del model viene ricavata dall'ancora. Quindi teoricamente dovrebbe essere in scala con il resto delle distanze.
   vec4 world_pos = u_model * local_pos;

   // Construct two vectors that are orthogonal to the normal.
   // This arbitrary choice is not co-linear with either horizontal
   // or vertical plane normals.
   const vec3 arbitrary = vec3(1.0, 1.0, 0.0);
   vec3 vec_u = normalize(cross(u_normal, arbitrary));
   vec3 vec_v = normalize(cross(u_normal, vec_u));

   // Project vertices in world frame onto vec_u and vec_v.
   vec2 uv = vec2(dot(world_pos.xyz, vec_u), dot(world_pos.xyz, vec_v));

   v_texCoordAlpha = vec3(u_planeUvMatrix * uv, a_positionXZAlpha.z);
   gl_Position = u_modelViewProjection * local_pos;
}
