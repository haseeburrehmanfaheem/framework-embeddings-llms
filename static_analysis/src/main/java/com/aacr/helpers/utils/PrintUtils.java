package com.aacr.helpers.utils;

import org.apache.commons.lang3.StringUtils;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class PrintUtils {
    public static boolean toFile = false;
    public static String fileName = "DefaultName.txt";

    public static void print(String s){
        System.out.println(s);
    }

    public static String blankN(int n){
        String r = "";
        for(int i = 0; i < n; ++i){
            r = r.concat("--");
        }
        r = r.concat("+-");
        return r;
    }

    public static void print_box(String s){
        int _length = s.length();
        String hash = StringUtils.repeat("-", _length);
        print(hash);
        print(s);
        print(hash);
    }

    public static void printBold(String s){
        if(toFile){
            print(s);
        }else{
            print("\033[1m" + s + "\033[0m");
        }
    }

    public static void setOutputStreamToFile(boolean value) throws FileNotFoundException {
        toFile = value;
        if(value){
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("(MM-dd-uu)-(HH:mm:ss)");
            toFile = false;
            fileName = "Logs/Log_" + dtf.format(LocalDateTime.now()) + ".txt";
            printBold("> Logging Output to File: " + fileName + " ...");
            toFile = true;
            PrintStream out = new PrintStream(new FileOutputStream(fileName));
            System.setOut(out);
        }

    }

    public static void setOutputStreamToFile(boolean value, String file) throws FileNotFoundException {
        toFile = value;
        if(value){
            toFile = false;
            fileName = file;
            printBold("> Logging Output to File: " + fileName + " ...");
            toFile = true;
            PrintStream out = new PrintStream(new FileOutputStream(fileName));
            System.setOut(out);
        }

    }


    public static void printToFile(String filename, ArrayList<String> lines) {
        try {
            PrintStream out = new PrintStream(new FileOutputStream(filename));
            for(String line : lines){
                out.println(line);
            }
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
}
