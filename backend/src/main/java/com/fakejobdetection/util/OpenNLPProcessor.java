package com.fakejobdetection.util;

import opennlp.tools.sentdetect.*;
import opennlp.tools.tokenize.*;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("UseSpecificCatch")
public class OpenNLPProcessor {

    private static Tokenizer tokenizer;
    private static SentenceDetectorME sentenceDetector;

    static {
        try {
            InputStream tokenModel =
                    OpenNLPProcessor.class.getResourceAsStream("/models/en-token.bin");
            InputStream sentModel =
                    OpenNLPProcessor.class.getResourceAsStream("/models/en-sent.bin");

            if (tokenModel == null || sentModel == null) {
                throw new RuntimeException("OpenNLP model files not found in /models");
            }

            tokenizer = new TokenizerME(new TokenizerModel(tokenModel));
            sentenceDetector = new SentenceDetectorME(new SentenceModel(sentModel));

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize OpenNLPProcessor", e);
        }
    }

    public static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.asList(tokenizer.tokenize(text));
    }

    public static int sentenceCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return sentenceDetector.sentDetect(text).length;
    }
}
