attribute vec2 a_pos;
attribute vec2 a_textCoord;

varying vec2 v_textCoord;

uniform mat4 u_modelMatrix;

void main(){
    vec4 world_pos = u_modelMatrix * vec4(a_pos.x, a_pos.y, 1.0, 1.0);
    //vec4 world_pos = u_modelMatrix * a_pos;
    gl_Position = world_pos;
    v_textCoord = a_textCoord;
}