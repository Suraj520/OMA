attribute vec4 a_position;
attribute vec2 a_textCoord;

varying vec2 v_textCoord;

void main() {
   gl_Position = a_position;
   v_textCoord = a_textCoord;
}
