package com.venussystem.venusmobile.model;

public enum HairType {
    STRAIGHT,
    WAVY,
    CURLY,
    COILY,
    OTHER;

    public static HairType daOpcao(String opcao) {
        if (opcao == null) {
            return OTHER;
        }
        switch (opcao.toUpperCase()) {
            case "1":
                return STRAIGHT;
            case "2A":
            case "2B":
            case "2C":
                return WAVY;
            case "3A":
            case "3B":
            case "3C":
                return CURLY;
            case "4A":
            case "4B":
            case "4C":
                return COILY;
            default:
                return OTHER;
        }
    }
}
