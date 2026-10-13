package com.venussystem.venusmobile.view.util;

import com.google.gson.Gson;
import com.venussystem.venusmobile.model.ScanFrontData;
import com.venussystem.venusmobile.model.ScanOcrResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/** Golden results captured before decomposition; these freeze behavior, not OCR accuracy. */
public class ScanAnalysisCharacterizationTest {
    private static final String[][] LABELS = {
        {}, {"DOVE", "ANTITRANSPIRANTE", "ORIGINAL", "150 ML"},
        {"LA ROCHE", "POSAY", "ANTHELIOS", "FPS 60", "50 ML"},
        {"PANTENE", "SHAMPOO", "HIDRATAÇÃO", "400 ML"},
        {"CERAVE", "CREME HIDRATANTE", "PELE SECA", "454 G"},
        {"NATURA", "EKOS", "PERFUME", "100 ML"},
        {"MAYBELLINE", "MASCARA PARA CILIOS", "10 ML"},
        {"MARCA TESTE", "CHOCOLATE", "90 G"},
        {"DETERGENTE", "LIMPEZA DOMESTICA", "500 ML"},
        {"CREME"}, {"GEL", "BODY", "BEAUTY"},
        {"Android Studio", "Logcat", "com.example", "SHAMPOO"},
        {"REXONA", "ANTITRANSPIRNTE", "48H", "150ML"},
        {"SKALA", "CREME PARA PENTEAR", "CABELOS CACHEADOS", "1000 G"},
        {"INGREDIENTES: AQUA, PARFUM", "PRECAUÇÕES", "www.example.com"},
        {"BABY SHAMPOO", "JOHNSONS", "200 ML"}
    };
    private static final String[] EXPECTED = {
        "48061b1ba2d75b5fe0ce4828bc10bdc8ad59ea2247d4ab000847266a5056c37b",
        "59d9625c80fbbcf887bff2b2f5cf359039c83e6fb86be2f64194cefc7967d17e",
        "534e22622913369539d109a175ced9fca562996c73e0ccd056d26ae2451c5f8b",
        "f964d1addf4085c76620b27634f5bc1c035f0bfbddc5f6b48197af1befe615b0",
        "78bfdd82d2228a98ee7f53fbb561a16c7b1469ae3873f9ee2613330da53d41f9",
        "69ddefab27a20fc870e8fc9374e3da82835e3817699279acfc57915f82c134af",
        "fa037fc4ffb87a1bcc707a15bd3d55c7e30efbac27bf3bc171596d45d20922b5",
        "297d6da635ed3b69239edf688ceec2b4f5c86fb5ec5baba7baef9f81369fdb68",
        "473835ac6da7be728ac6217041b451b5652bcdc626bee769bddf360f6bb7b365",
        "4149bc457ab54bab68296b58d15143619a5e5d08f9aa6ec475c611677aee66b2",
        "66753e2caa5400e3395e63cd3b25b352c407b77e13879d79add6824b95688291",
        "0ff34635d440aab13c6c2297f5db0aeed998d022c9c71996bfec0b398c0d4779",
        "10123cbe2e53778dd3fc05e279310f840e28285f39ba98946832cd8f387b2af3",
        "6433baf793c135a2cb1a9a6d0f6a5fd3d0142d1ebaf069beac6d5e2183a6b6f0",
        "2b83e6b85326e461b611c338c0f9acb5d46288cbaf9fa8109b9ef24ae447e0e9",
        "e0e4e27c4412ac32421791612cf31195342c3b800533602f301cf07e2ef11a92"
    };

    @Test public void frontAndClassificationPreserveAllGoldenFields() throws Exception {
        assertEquals("One baseline per fixture",LABELS.length,EXPECTED.length);
        for (int i=0;i<LABELS.length;i++) {
            String json = result(LABELS[i]);
            assertEquals("Fixture " + i + ": " + json,EXPECTED[i],digest(json));
        }
    }
    private static String result(String[] label) {
        List<String> lines = Arrays.asList(label);
        ScanFrontData front = ScanFrontExtractor.extract(lines);
        ScanOcrResult ocr = new ScanOcrResult(String.join("\n",lines),lines,Collections.emptyList());
        return new Gson().toJson(Arrays.asList(front,ScanCosmeticClassifier.classify(ocr,front)));
    }
    private static String digest(String value) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for(byte b:bytes) hex.append(String.format(java.util.Locale.ROOT,"%02x",b & 255));
        return hex.toString();
    }
    public static void main(String[] args) throws Exception {
        for(String[] label:LABELS) System.out.println("\"" + digest(result(label)) + "\",");
    }
}
