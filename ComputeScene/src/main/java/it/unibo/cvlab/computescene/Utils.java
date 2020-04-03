/*
Copyright 2019 Filippo Aleotti

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

* Author: Filippo Aleotti
* Mail: filippo.aleotti2@unibo.it
*/

package it.unibo.cvlab.computescene;

import java.util.ArrayList;
import java.util.List;

public class Utils {

    public enum Scale {
        FULL(1.f), HALF(0.5f), QUARTER(0.25f), HEIGHT(0.125f);

        private float value;

        Scale(float value){
            this.value = value;
        }

        public float getValue(){
            return this.value;
        }

        public String toString(){
            switch(this){
                case FULL:
                    return "Full";
                case HALF:
                    return "Half";
                case QUARTER:
                    return "Quarter";
                case HEIGHT:
                    return "Height";
            }
            return "Not valid resolution";
        }
    }

    public enum Resolution{
        RES1(512,256), RES2(640,192), RES3(320,96), RES4(640,448), SER4(448, 640);

        private final int width;
        private final int height;

        Resolution(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public String toString(){
            return ""+this.width+"x"+this.height;
        }

        public int getWidth(){
            return this.width;
        }

        public int getHeight(){
            return this.height;
        }
    }

    public static List<Integer> getPlasma(){
        // A better approach would be read the colormap from assets...
        List<Integer> color = new ArrayList<>();
        color.add(0xF0F921);
        color.add(0xF0F724);
        color.add(0xF1F525);
        color.add(0xF1F426);
        color.add(0xF2F227);
        color.add(0xF3F027);
        color.add(0xF3EE27);
        color.add(0xF4ED27);
        color.add(0xF5EB27);
        color.add(0xF5E926);
        color.add(0xF6E826);
        color.add(0xF6E626);
        color.add(0xF7E425);
        color.add(0xF7E225);
        color.add(0xF8E125);
        color.add(0xF8DF25);
        color.add(0xF9DD25);
        color.add(0xF9DC24);
        color.add(0xFADA24);
        color.add(0xFAD824);
        color.add(0xFBD724);
        color.add(0xFBD524);
        color.add(0xFBD324);
        color.add(0xFCD225);
        color.add(0xFCD025);
        color.add(0xFCCE25);
        color.add(0xFCCD25);
        color.add(0xFDCB26);
        color.add(0xFDCA26);
        color.add(0xFDC827);
        color.add(0xFDC627);
        color.add(0xFDC527);
        color.add(0xFDC328);
        color.add(0xFDC229);
        color.add(0xFEC029);
        color.add(0xFEBE2A);
        color.add(0xFEBD2A);
        color.add(0xFEBB2B);
        color.add(0xFEBA2C);
        color.add(0xFEB82C);
        color.add(0xFEB72D);
        color.add(0xFDB52E);
        color.add(0xFDB42F);
        color.add(0xFDB22F);
        color.add(0xFDB130);
        color.add(0xFDAF31);
        color.add(0xFDAE32);
        color.add(0xFDAC33);
        color.add(0xFDAB33);
        color.add(0xFCA934);
        color.add(0xFCA835);
        color.add(0xFCA636);
        color.add(0xFCA537);
        color.add(0xFCA338);
        color.add(0xFBA238);
        color.add(0xFBA139);
        color.add(0xFB9F3A);
        color.add(0xFA9E3B);
        color.add(0xFA9C3C);
        color.add(0xFA9B3D);
        color.add(0xF99A3E);
        color.add(0xF9983E);
        color.add(0xF9973F);
        color.add(0xF89540);
        color.add(0xF89441);
        color.add(0xF79342);
        color.add(0xF79143);
        color.add(0xF79044);
        color.add(0xF68F44);
        color.add(0xF68D45);
        color.add(0xF58C46);
        color.add(0xF58B47);
        color.add(0xF48948);
        color.add(0xF48849);
        color.add(0xF3874A);
        color.add(0xF3854B);
        color.add(0xF2844B);
        color.add(0xF1834C);
        color.add(0xF1814D);
        color.add(0xF0804E);
        color.add(0xF07F4F);
        color.add(0xEF7E50);
        color.add(0xEF7C51);
        color.add(0xEE7B51);
        color.add(0xED7A52);
        color.add(0xED7953);
        color.add(0xEC7754);
        color.add(0xEB7655);
        color.add(0xEB7556);
        color.add(0xEA7457);
        color.add(0xE97257);
        color.add(0xE97158);
        color.add(0xE87059);
        color.add(0xE76F5A);
        color.add(0xE76E5B);
        color.add(0xE66C5C);
        color.add(0xE56B5D);
        color.add(0xE56A5D);
        color.add(0xE4695E);
        color.add(0xE3685F);
        color.add(0xE26660);
        color.add(0xE26561);
        color.add(0xE16462);
        color.add(0xE06363);
        color.add(0xDF6263);
        color.add(0xDE6164);
        color.add(0xDE5F65);
        color.add(0xDD5E66);
        color.add(0xDC5D67);
        color.add(0xDB5C68);
        color.add(0xDA5B69);
        color.add(0xDA5A6A);
        color.add(0xD9586A);
        color.add(0xD8576B);
        color.add(0xD7566C);
        color.add(0xD6556D);
        color.add(0xD5546E);
        color.add(0xD5536F);
        color.add(0xD45270);
        color.add(0xD35171);
        color.add(0xD24F71);
        color.add(0xD14E72);
        color.add(0xD04D73);
        color.add(0xCF4C74);
        color.add(0xCE4B75);
        color.add(0xCD4A76);
        color.add(0xCC4977);
        color.add(0xCC4778);
        color.add(0xCB4679);
        color.add(0xCA457A);
        color.add(0xC9447A);
        color.add(0xC8437B);
        color.add(0xC7427C);
        color.add(0xC6417D);
        color.add(0xC5407E);
        color.add(0xC43E7F);
        color.add(0xC33D80);
        color.add(0xC23C81);
        color.add(0xC13B82);
        color.add(0xC03A83);
        color.add(0xBF3984);
        color.add(0xBE3885);
        color.add(0xBD3786);
        color.add(0xBC3587);
        color.add(0xBB3488);
        color.add(0xBA3388);
        color.add(0xB83289);
        color.add(0xB7318A);
        color.add(0xB6308B);
        color.add(0xB52F8C);
        color.add(0xB42E8D);
        color.add(0xB32C8E);
        color.add(0xB22B8F);
        color.add(0xB12A90);
        color.add(0xB02991);
        color.add(0xAE2892);
        color.add(0xAD2793);
        color.add(0xAC2694);
        color.add(0xAB2494);
        color.add(0xAA2395);
        color.add(0xA82296);
        color.add(0xA72197);
        color.add(0xA62098);
        color.add(0xA51F99);
        color.add(0xA31E9A);
        color.add(0xA21D9A);
        color.add(0xA11B9B);
        color.add(0xA01A9C);
        color.add(0x9E199D);
        color.add(0x9D189D);
        color.add(0x9C179E);
        color.add(0x9A169F);
        color.add(0x99159F);
        color.add(0x9814A0);
        color.add(0x9613A1);
        color.add(0x9511A1);
        color.add(0x9410A2);
        color.add(0x920FA3);
        color.add(0x910EA3);
        color.add(0x8F0DA4);
        color.add(0x8E0CA4);
        color.add(0x8D0BA5);
        color.add(0x8B0AA5);
        color.add(0x8A09A5);
        color.add(0x8808A6);
        color.add(0x8707A6);
        color.add(0x8606A6);
        color.add(0x8405A7);
        color.add(0x8305A7);
        color.add(0x8104A7);
        color.add(0x8004A8);
        color.add(0x7E03A8);
        color.add(0x7D03A8);
        color.add(0x7B02A8);
        color.add(0x7A02A8);
        color.add(0x7801A8);
        color.add(0x7701A8);
        color.add(0x7501A8);
        color.add(0x7401A8);
        color.add(0x7201A8);
        color.add(0x7100A8);
        color.add(0x6F00A8);
        color.add(0x6E00A8);
        color.add(0x6C00A8);
        color.add(0x6A00A8);
        color.add(0x6900A8);
        color.add(0x6700A8);
        color.add(0x6600A7);
        color.add(0x6400A7);
        color.add(0x6300A7);
        color.add(0x6100A7);
        color.add(0x6001A6);
        color.add(0x5E01A6);
        color.add(0x5C01A6);
        color.add(0x5B01A5);
        color.add(0x5901A5);
        color.add(0x5801A4);
        color.add(0x5601A4);
        color.add(0x5502A4);
        color.add(0x5302A3);
        color.add(0x5102A3);
        color.add(0x5002A2);
        color.add(0x4E02A2);
        color.add(0x4C02A1);
        color.add(0x4B03A1);
        color.add(0x4903A0);
        color.add(0x48039F);
        color.add(0x46039F);
        color.add(0x44039E);
        color.add(0x43039E);
        color.add(0x41049D);
        color.add(0x3F049C);
        color.add(0x3E049C);
        color.add(0x3C049B);
        color.add(0x3A049A);
        color.add(0x38049A);
        color.add(0x370499);
        color.add(0x350498);
        color.add(0x330597);
        color.add(0x310597);
        color.add(0x2F0596);
        color.add(0x2E0595);
        color.add(0x2C0594);
        color.add(0x2A0593);
        color.add(0x280592);
        color.add(0x260591);
        color.add(0x240691);
        color.add(0x220690);
        color.add(0x20068F);
        color.add(0x1D068E);
        color.add(0x1B068D);
        color.add(0x19068C);
        color.add(0x16078A);
        color.add(0x130789);
        color.add(0x100788);
        color.add(0x0D0887);
        
        return color;
    }

    public static final float PLASMA_FACTOR = 10.5f;

    public static final float[] PLASMA =
            new float[]{
                    0.9411765f, 0.9764706f, 0.12941177f,
                    0.9411765f, 0.96862745f, 0.14117648f,
                    0.94509804f, 0.9607843f, 0.14509805f,
                    0.94509804f, 0.95686275f, 0.14901961f,
                    0.9490196f, 0.9490196f, 0.15294118f,
                    0.9529412f, 0.9411765f, 0.15294118f,
                    0.9529412f, 0.93333334f, 0.15294118f,
                    0.95686275f, 0.92941177f, 0.15294118f,
                    0.9607843f, 0.92156863f, 0.15294118f,
                    0.9607843f, 0.9137255f, 0.14901961f,
                    0.9647059f, 0.9098039f, 0.14901961f,
                    0.9647059f, 0.9019608f, 0.14901961f,
                    0.96862745f, 0.89411765f, 0.14509805f,
                    0.96862745f, 0.8862745f, 0.14509805f,
                    0.972549f, 0.88235295f, 0.14509805f,
                    0.972549f, 0.8745098f, 0.14509805f,
                    0.9764706f, 0.8666667f, 0.14509805f,
                    0.9764706f, 0.8627451f, 0.14117648f,
                    0.98039216f, 0.85490197f, 0.14117648f,
                    0.98039216f, 0.84705883f, 0.14117648f,
                    0.9843137f, 0.84313726f, 0.14117648f,
                    0.9843137f, 0.8352941f, 0.14117648f,
                    0.9843137f, 0.827451f, 0.14117648f,
                    0.9882353f, 0.8235294f, 0.14509805f,
                    0.9882353f, 0.8156863f, 0.14509805f,
                    0.9882353f, 0.80784315f, 0.14509805f,
                    0.9882353f, 0.8039216f, 0.14509805f,
                    0.99215686f, 0.79607844f, 0.14901961f,
                    0.99215686f, 0.7921569f, 0.14901961f,
                    0.99215686f, 0.78431374f, 0.15294118f,
                    0.99215686f, 0.7764706f, 0.15294118f,
                    0.99215686f, 0.77254903f, 0.15294118f,
                    0.99215686f, 0.7647059f, 0.15686275f,
                    0.99215686f, 0.7607843f, 0.16078432f,
                    0.99607843f, 0.7529412f, 0.16078432f,
                    0.99607843f, 0.74509805f, 0.16470589f,
                    0.99607843f, 0.7411765f, 0.16470589f,
                    0.99607843f, 0.73333335f, 0.16862746f,
                    0.99607843f, 0.7294118f, 0.17254902f,
                    0.99607843f, 0.72156864f, 0.17254902f,
                    0.99607843f, 0.7176471f, 0.1764706f,
                    0.99215686f, 0.70980394f, 0.18039216f,
                    0.99215686f, 0.7058824f, 0.18431373f,
                    0.99215686f, 0.69803923f, 0.18431373f,
                    0.99215686f, 0.69411767f, 0.1882353f,
                    0.99215686f, 0.6862745f, 0.19215687f,
                    0.99215686f, 0.68235296f, 0.19607843f,
                    0.99215686f, 0.6745098f, 0.2f,
                    0.99215686f, 0.67058825f, 0.2f,
                    0.9882353f, 0.6627451f, 0.20392157f,
                    0.9882353f, 0.65882355f, 0.20784314f,
                    0.9882353f, 0.6509804f, 0.21176471f,
                    0.9882353f, 0.64705884f, 0.21568628f,
                    0.9882353f, 0.6392157f, 0.21960784f,
                    0.9843137f, 0.63529414f, 0.21960784f,
                    0.9843137f, 0.6313726f, 0.22352941f,
                    0.9843137f, 0.62352943f, 0.22745098f,
                    0.98039216f, 0.61960787f, 0.23137255f,
                    0.98039216f, 0.6117647f, 0.23529412f,
                    0.98039216f, 0.60784316f, 0.23921569f,
                    0.9764706f, 0.6039216f, 0.24313726f,
                    0.9764706f, 0.59607846f, 0.24313726f,
                    0.9764706f, 0.5921569f, 0.24705882f,
                    0.972549f, 0.58431375f, 0.2509804f,
                    0.972549f, 0.5803922f, 0.25490198f,
                    0.96862745f, 0.5764706f, 0.25882354f,
                    0.96862745f, 0.5686275f, 0.2627451f,
                    0.96862745f, 0.5647059f, 0.26666668f,
                    0.9647059f, 0.56078434f, 0.26666668f,
                    0.9647059f, 0.5529412f, 0.27058825f,
                    0.9607843f, 0.54901963f, 0.27450982f,
                    0.9607843f, 0.54509807f, 0.2784314f,
                    0.95686275f, 0.5372549f, 0.28235295f,
                    0.95686275f, 0.53333336f, 0.28627452f,
                    0.9529412f, 0.5294118f, 0.2901961f,
                    0.9529412f, 0.52156866f, 0.29411766f,
                    0.9490196f, 0.5176471f, 0.29411766f,
                    0.94509804f, 0.5137255f, 0.29803923f,
                    0.94509804f, 0.5058824f, 0.3019608f,
                    0.9411765f, 0.5019608f, 0.30588236f,
                    0.9411765f, 0.49803922f, 0.30980393f,
                    0.9372549f, 0.49411765f, 0.3137255f,
                    0.9372549f, 0.4862745f, 0.31764707f,
                    0.93333334f, 0.48235294f, 0.31764707f,
                    0.92941177f, 0.47843137f, 0.32156864f,
                    0.92941177f, 0.4745098f, 0.3254902f,
                    0.9254902f, 0.46666667f, 0.32941177f,
                    0.92156863f, 0.4627451f, 0.33333334f,
                    0.92156863f, 0.45882353f, 0.3372549f,
                    0.91764706f, 0.45490196f, 0.34117648f,
                    0.9137255f, 0.44705883f, 0.34117648f,
                    0.9137255f, 0.44313726f, 0.34509805f,
                    0.9098039f, 0.4392157f, 0.34901962f,
                    0.90588236f, 0.43529412f, 0.3529412f,
                    0.90588236f, 0.43137255f, 0.35686275f,
                    0.9019608f, 0.42352942f, 0.36078432f,
                    0.8980392f, 0.41960785f, 0.3647059f,
                    0.8980392f, 0.41568628f, 0.3647059f,
                    0.89411765f, 0.4117647f, 0.36862746f,
                    0.8901961f, 0.40784314f, 0.37254903f,
                    0.8862745f, 0.4f, 0.3764706f,
                    0.8862745f, 0.39607844f, 0.38039216f,
                    0.88235295f, 0.39215687f, 0.38431373f,
                    0.8784314f, 0.3882353f, 0.3882353f,
                    0.8745098f, 0.38431373f, 0.3882353f,
                    0.87058824f, 0.38039216f, 0.39215687f,
                    0.87058824f, 0.37254903f, 0.39607844f,
                    0.8666667f, 0.36862746f, 0.4f,
                    0.8627451f, 0.3647059f, 0.40392157f,
                    0.85882354f, 0.36078432f, 0.40784314f,
                    0.85490197f, 0.35686275f, 0.4117647f,
                    0.85490197f, 0.3529412f, 0.41568628f,
                    0.8509804f, 0.34509805f, 0.41568628f,
                    0.84705883f, 0.34117648f, 0.41960785f,
                    0.84313726f, 0.3372549f, 0.42352942f,
                    0.8392157f, 0.33333334f, 0.42745098f,
                    0.8352941f, 0.32941177f, 0.43137255f,
                    0.8352941f, 0.3254902f, 0.43529412f,
                    0.83137256f, 0.32156864f, 0.4392157f,
                    0.827451f, 0.31764707f, 0.44313726f,
                    0.8235294f, 0.30980393f, 0.44313726f,
                    0.81960785f, 0.30588236f, 0.44705883f,
                    0.8156863f, 0.3019608f, 0.4509804f,
                    0.8117647f, 0.29803923f, 0.45490196f,
                    0.80784315f, 0.29411766f, 0.45882353f,
                    0.8039216f, 0.2901961f, 0.4627451f,
                    0.8f, 0.28627452f, 0.46666667f,
                    0.8f, 0.2784314f, 0.47058824f,
                    0.79607844f, 0.27450982f, 0.4745098f,
                    0.7921569f, 0.27058825f, 0.47843137f,
                    0.7882353f, 0.26666668f, 0.47843137f,
                    0.78431374f, 0.2627451f, 0.48235294f,
                    0.78039217f, 0.25882354f, 0.4862745f,
                    0.7764706f, 0.25490198f, 0.49019608f,
                    0.77254903f, 0.2509804f, 0.49411765f,
                    0.76862746f, 0.24313726f, 0.49803922f,
                    0.7647059f, 0.23921569f, 0.5019608f,
                    0.7607843f, 0.23529412f, 0.5058824f,
                    0.75686276f, 0.23137255f, 0.50980395f,
                    0.7529412f, 0.22745098f, 0.5137255f,
                    0.7490196f, 0.22352941f, 0.5176471f,
                    0.74509805f, 0.21960784f, 0.52156866f,
                    0.7411765f, 0.21568628f, 0.5254902f,
                    0.7372549f, 0.20784314f, 0.5294118f,
                    0.73333335f, 0.20392157f, 0.53333336f,
                    0.7294118f, 0.2f, 0.53333336f,
                    0.72156864f, 0.19607843f, 0.5372549f,
                    0.7176471f, 0.19215687f, 0.5411765f,
                    0.7137255f, 0.1882353f, 0.54509807f,
                    0.70980394f, 0.18431373f, 0.54901963f,
                    0.7058824f, 0.18039216f, 0.5529412f,
                    0.7019608f, 0.17254902f, 0.5568628f,
                    0.69803923f, 0.16862746f, 0.56078434f,
                    0.69411767f, 0.16470589f, 0.5647059f,
                    0.6901961f, 0.16078432f, 0.5686275f,
                    0.68235296f, 0.15686275f, 0.57254905f,
                    0.6784314f, 0.15294118f, 0.5764706f,
                    0.6745098f, 0.14901961f, 0.5803922f,
                    0.67058825f, 0.14117648f, 0.5803922f,
                    0.6666667f, 0.13725491f, 0.58431375f,
                    0.65882355f, 0.13333334f, 0.5882353f,
                    0.654902f, 0.12941177f, 0.5921569f,
                    0.6509804f, 0.1254902f, 0.59607846f,
                    0.64705884f, 0.12156863f, 0.6f,
                    0.6392157f, 0.11764706f, 0.6039216f,
                    0.63529414f, 0.11372549f, 0.6039216f,
                    0.6313726f, 0.105882354f, 0.60784316f,
                    0.627451f, 0.101960786f, 0.6117647f,
                    0.61960787f, 0.09803922f, 0.6156863f,
                    0.6156863f, 0.09411765f, 0.6156863f,
                    0.6117647f, 0.09019608f, 0.61960787f,
                    0.6039216f, 0.08627451f, 0.62352943f,
                    0.6f, 0.08235294f, 0.62352943f,
                    0.59607846f, 0.078431375f, 0.627451f,
                    0.5882353f, 0.07450981f, 0.6313726f,
                    0.58431375f, 0.06666667f, 0.6313726f,
                    0.5803922f, 0.0627451f, 0.63529414f,
                    0.57254905f, 0.05882353f, 0.6392157f,
                    0.5686275f, 0.05490196f, 0.6392157f,
                    0.56078434f, 0.050980393f, 0.6431373f,
                    0.5568628f, 0.047058824f, 0.6431373f,
                    0.5529412f, 0.043137256f, 0.64705884f,
                    0.54509807f, 0.039215688f, 0.64705884f,
                    0.5411765f, 0.03529412f, 0.64705884f,
                    0.53333336f, 0.03137255f, 0.6509804f,
                    0.5294118f, 0.02745098f, 0.6509804f,
                    0.5254902f, 0.023529412f, 0.6509804f,
                    0.5176471f, 0.019607844f, 0.654902f,
                    0.5137255f, 0.019607844f, 0.654902f,
                    0.5058824f, 0.015686275f, 0.654902f,
                    0.5019608f, 0.015686275f, 0.65882355f,
                    0.49411765f, 0.011764706f, 0.65882355f,
                    0.49019608f, 0.011764706f, 0.65882355f,
                    0.48235294f, 0.007843138f, 0.65882355f,
                    0.47843137f, 0.007843138f, 0.65882355f,
                    0.47058824f, 0.003921569f, 0.65882355f,
                    0.46666667f, 0.003921569f, 0.65882355f,
                    0.45882353f, 0.003921569f, 0.65882355f,
                    0.45490196f, 0.003921569f, 0.65882355f,
                    0.44705883f, 0.003921569f, 0.65882355f,
                    0.44313726f, 0.0f, 0.65882355f,
                    0.43529412f, 0.0f, 0.65882355f,
                    0.43137255f, 0.0f, 0.65882355f,
                    0.42352942f, 0.0f, 0.65882355f,
                    0.41568628f, 0.0f, 0.65882355f,
                    0.4117647f, 0.0f, 0.65882355f,
                    0.40392157f, 0.0f, 0.65882355f,
                    0.4f, 0.0f, 0.654902f,
                    0.39215687f, 0.0f, 0.654902f,
                    0.3882353f, 0.0f, 0.654902f,
                    0.38039216f, 0.0f, 0.654902f,
                    0.3764706f, 0.003921569f, 0.6509804f,
                    0.36862746f, 0.003921569f, 0.6509804f,
                    0.36078432f, 0.003921569f, 0.6509804f,
                    0.35686275f, 0.003921569f, 0.64705884f,
                    0.34901962f, 0.003921569f, 0.64705884f,
                    0.34509805f, 0.003921569f, 0.6431373f,
                    0.3372549f, 0.003921569f, 0.6431373f,
                    0.33333334f, 0.007843138f, 0.6431373f,
                    0.3254902f, 0.007843138f, 0.6392157f,
                    0.31764707f, 0.007843138f, 0.6392157f,
                    0.3137255f, 0.007843138f, 0.63529414f,
                    0.30588236f, 0.007843138f, 0.63529414f,
                    0.29803923f, 0.007843138f, 0.6313726f,
                    0.29411766f, 0.011764706f, 0.6313726f,
                    0.28627452f, 0.011764706f, 0.627451f,
                    0.28235295f, 0.011764706f, 0.62352943f,
                    0.27450982f, 0.011764706f, 0.62352943f,
                    0.26666668f, 0.011764706f, 0.61960787f,
                    0.2627451f, 0.011764706f, 0.61960787f,
                    0.25490198f, 0.015686275f, 0.6156863f,
                    0.24705882f, 0.015686275f, 0.6117647f,
                    0.24313726f, 0.015686275f, 0.6117647f,
                    0.23529412f, 0.015686275f, 0.60784316f,
                    0.22745098f, 0.015686275f, 0.6039216f,
                    0.21960784f, 0.015686275f, 0.6039216f,
                    0.21568628f, 0.015686275f, 0.6f,
                    0.20784314f, 0.015686275f, 0.59607846f,
                    0.2f, 0.019607844f, 0.5921569f,
                    0.19215687f, 0.019607844f, 0.5921569f,
                    0.18431373f, 0.019607844f, 0.5882353f,
                    0.18039216f, 0.019607844f, 0.58431375f,
                    0.17254902f, 0.019607844f, 0.5803922f,
                    0.16470589f, 0.019607844f, 0.5764706f,
                    0.15686275f, 0.019607844f, 0.57254905f,
                    0.14901961f, 0.019607844f, 0.5686275f,
                    0.14117648f, 0.023529412f, 0.5686275f,
                    0.13333334f, 0.023529412f, 0.5647059f,
                    0.1254902f, 0.023529412f, 0.56078434f,
                    0.11372549f, 0.023529412f, 0.5568628f,
                    0.105882354f, 0.023529412f, 0.5529412f,
                    0.09803922f, 0.023529412f, 0.54901963f,
                    0.08627451f, 0.02745098f, 0.5411765f,
                    0.07450981f, 0.02745098f, 0.5372549f,
                    0.0627451f, 0.02745098f, 0.53333336f,
                    0.050980393f, 0.03137255f, 0.5294118f,
            };
}
