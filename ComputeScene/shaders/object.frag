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

#ifdef GL_ES
precision mediump float;
#endif

uniform sampler2D u_texture;
uniform sampler2D u_inferenceTexture;

uniform float u_maskEnabled;

uniform float u_lowerDelta;

uniform vec2 u_windowSize;

varying vec2 v_texCoord;

uniform vec4 u_objColor;

varying vec3 v_worldPos;
uniform vec3 u_cameraPose;
uniform float u_scaleFactor;
uniform float u_shiftFactor;

void main() {
    if(u_maskEnabled > 0.5){
        //https://community.khronos.org/t/confused-about-gl-fragcoord-use-with-textures/67832/3
        //https://community.khronos.org/t/gl-fragcoord-z-gl-fragcoord-w-for-quick-depth-calculation-camera-to-fragment/68919/2
        vec2 texcoord = (gl_FragCoord.xy - vec2(0.5,0.5)) / u_windowSize;

        //Sistema opengl: origine in bottom left.
        //Sistema coordinate predizione: origine in top-left

        //Inverto solo y
        texcoord = vec2(texcoord.x, 1.0-texcoord.y);

        vec4 inferenceVector = texture2D(u_inferenceTexture, texcoord);

        float predictedDistance = inferenceVector.r * u_scaleFactor + u_shiftFactor;

        float dx = u_cameraPose.x-v_worldPos.x;
        float dy = u_cameraPose.y-v_worldPos.y;
        float dz = u_cameraPose.z-v_worldPos.z;

        float distance = sqrt(dx*dx+dy*dy+dz*dz);

        if(distance > predictedDistance + u_lowerDelta){
            discard;
        }
    }

    // Flip the y-texture coordinate to address the texture from top-left.
    vec4 objectColor = texture2D(u_texture, vec2(v_texCoord.x, 1.0 - v_texCoord.y));
    //vec4 objectColor = vec4(tmp.b, tmp.g, tmp.r, 1.0);

    // Apply color to grayscale image only if the alpha of u_ObjColor is
    // greater and equal to 255.0.
    if (u_objColor.a >= 255.0) {
      float intensity = objectColor.r;
      objectColor.rgb = u_objColor.rgb * intensity / 255.0;
    }

    gl_FragColor = objectColor;
}
