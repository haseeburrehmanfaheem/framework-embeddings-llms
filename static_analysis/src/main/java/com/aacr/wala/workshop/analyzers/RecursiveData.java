package com.aacr.wala.workshop.analyzers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



public class RecursiveData {
        public ArrayList<String> instructions;
        public ArrayList<String> protections;
        public ArrayList<String> invokes;

        public RecursiveData(ArrayList<String> instructions, ArrayList<String> protections, ArrayList<String> invokes){
            this.instructions = instructions;
            this.protections = protections;
            this.invokes = invokes;
        }

        public String getInstructionsString(){

            return DataCollection.join(this.instructions, " <|> ");
        }
        public int getProtectionInt(){
            return analysePath();
        }
        public String getInvokesString(){
            return DataCollection.join(this.invokes, " <|> ");
        }

        public HashMap<String, String> getFeatures(){
            HashMap<String, String> features = new HashMap<>();
            features.put("instructions", getInstructionsString());
            features.put("invokes", getInvokesString());
            features.put("protection", Integer.toString(getProtectionInt()));
            return features;
        }

        public int analysePath(){
            int curLevel = -1;
            for(String ins: this.protections){
                if(ins.contains("[AC]")){
                    int num = extractNumberFromInstruction(ins);
                    curLevel = AND(curLevel, num);
                }
            }
            return curLevel;
        }

        public static int extractNumberFromInstruction(String instruction) {
            Pattern pattern = Pattern.compile("\\((\\d+)\\)");
            Matcher matcher = pattern.matcher(instruction);

            if (matcher.find()) {
                String numberString = matcher.group(1);
                return Integer.parseInt(numberString);
            }

            return -1; // If no number is found, return a default value or handle it accordingly
        }

        public static int AND(int _n1, int _n2){

            if(_n1 == 0){
                return _n2;
            }else if(_n2 == 0){
                return _n1;
            }

            if(_n1 > _n2){
                return _n1;
            }
            return _n2;
        }


    }

