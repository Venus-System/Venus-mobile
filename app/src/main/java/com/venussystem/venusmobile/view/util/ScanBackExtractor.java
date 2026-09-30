package com.venussystem.venusmobile.view.util;

import android.util.Log;

import androidx.annotation.NonNull;

import com.venussystem.venusmobile.model.ScanBackData;
import com.venussystem.venusmobile.model.ScanBackIngredientCandidate;
import com.venussystem.venusmobile.model.ScanOcrResult;
import com.venussystem.venusmobile.model.ScanOcrToken;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser estrutural do verso de embalagens.
 *
 * V7.1
 *
 * Objetivos:
 *
 * 1) preservar a maior quantidade possível de texto da composição;
 * 2) não transformar texto administrativo em ingrediente;
 * 3) não inventar fabricante, lote, país, conteúdo ou código de barras;
 * 4) tolerar OCR ruim em português/espanhol/inglês;
 * 5) entregar candidatos de ingredientes para um matcher posterior;
 * 6) impedir vazamento de telefone, e-mail, URL, CNPJ e CEP para ingredientes;
 * 7) tratar marcadores administrativos colados ao final de um ingrediente;
 * 8) manter candidatos OCR imperfeitos como UNRESOLVED.
 *
 * IMPORTANTE:
 *
 * Esta classe NÃO consulta banco, API, Mongo ou Cloudinary.
 *
 * O status inicial dos ingredientes permanece UNRESOLVED.
 *
 * Exemplo:
 *
 * OCR:
 *
 * INGREDIENTES: AQUA, GLYCNE, PARFUM
 * LINALOOL .FABRICADO
 * POR UNILEVER BRASIL LTDA
 * AUTFUNC: 2.05610-6
 *
 * O parser deve preservar:
 *
 * AQUA
 * GLYCNE
 * PARFUM
 * LINALOOL
 *
 * e impedir:
 *
 * FABRICADO
 * UNILEVER
 * AUTFUNC
 * 2.05610-6
 *
 * de entrarem como ingredientes.
 */
public final class ScanBackExtractor {

    private static final String TAG =
            "VENUS_BACK_PARSE";

    private static final String EMPTY =
            "";

    /*
     * ================================================================
     * HEADINGS
     * ================================================================
     */

    private static final String[] INGREDIENT_HEADINGS = {
            "INGREDIENTES",
            "INGREDIENTE",
            "INGREDIENTS",
            "INGREDIENT",
            "INGREDIENTI",
            "COMPOSICAO",
            "COMPOSITION",
            "COMPOSICION",
            "COMPOSICION INCI",
            "COMPOSICAO INCI",
            "INCI"
    };

    private static final String[] USAGE_HEADINGS = {
            "MODO DE USO",
            "MODO DE USAR",
            "MODO D USO",
            "ODO DE USO",
            "MODO DE EMPLEO",
            "INSTRUCOES DE USO",
            "INSTRUCOES PARA USO",
            "COMO USAR",
            "COMO UTILIZAR",
            "HOW TO USE",
            "DIRECTIONS FOR USE",
            "DIRECTIONS",
            "UTILIZACAO",
            "UTILIZACION"
    };

    private static final String[] PRECAUTION_HEADINGS = {
            "PRECAUCOES",
            "PRECAUCAO",
            "PRECAUTIONS",
            "CAUTIONS",
            "CUIDADOS",
            "CUIDADO",
            "ADVERTENCIA",
            "ADVERTENCIAS",
            "WARNING",
            "WARNINGS",
            "AVISOS",
            "PRECAUTION"
    };

    /*
     * ================================================================
     * EMPRESA / FABRICANTE
     * ================================================================
     */

    private static final String[] MANUFACTURER_MARKERS = {
            "FABRICADO POR",
            "FABRICADO",
            "FABRICANTE",
            "FABRICANT",
            "MANUFACTURER",
            "MANUFACTURED BY",
            "MANUFACTURED",
            "MADE BY",
            "PRODUZIDO POR",
            "PRODUZIDO",
            "MANUFACTURADO POR",
            "ELABORADO POR",
            "ELABORADO",
            "DISTRIBUTED BY"
    };

    private static final String[] IMPORTER_MARKERS = {
            "IMPORTADO POR",
            "IMPORTADOR",
            "IMPORTADO",
            "IMPORTED BY",
            "IMPORTED",
            "IMPORTADORA"
    };

    private static final String[] DISTRIBUTOR_MARKERS = {
            "DISTRIBUIDO POR",
            "DISTRIBUIDORA",
            "DISTRIBUTOR",
            "DISTRIBUTED BY"
    };

    /**
     * Marcadores fortes que encerram a seção de ingredientes.
     */
    private static final String[] INGREDIENT_SECTION_STOPS = {
            "FABRICADO",
            "FABRICANTE",
            "PRODUZIDO",
            "MANUFACTURER",
            "MANUFACTURED",
            "ELABORADO",
            "IMPORTADO",
            "IMPORTADOR",
            "IMPORTED",
            "DISTRIBUIDO",
            "DISTRIBUIDORA",
            "DISTRIBUTED BY",
            "CNPJ",
            "CEP",
            "AUTFUNC",
            "AUT FUNC",
            "AUT. FUNC",
            "AUTORIZACAO",
            "REGISTRO ANVISA",
            "NUMERO DE REGISTRO",
            "PROCESSO ANVISA",
            "PROCESSO",
            "CODIGO DE BARRAS",
            "CODIGO EAN",
            "EAN",
            "GTIN",
            "LOTE",
            "LQTE",
            "L0TE",
            "BATCH",
            "VALIDADE",
            "VENCIMENTO",
            "EXPIRY",
            "MODO DE USO",
            "MODO DE USAR",
            "MODO DE EMPLEO",
            "COMO USAR",
            "HOW TO USE",
            "PRECAUCOES",
            "PRECAUCAO",
            "PRECAUTIONS",
            "CAUTIONS",
            "ADVERTENCIA",
            "ADVERTENCIAS",
            "WARNING",
            "WARNINGS",
            "SAC",
            "ATENDIMENTO AO CONSUMIDOR",
            "CENTRAL DE ATENDIMENTO",
            "WHATSAPP",
            "CONSUMIDOR",
            "ENDERECO",
            "ENTRADA",
            "SARGENTO",
            "RUA",
            "AVENIDA",
            "ALAMEDA",
            "TRAVESSA",
            "RODOVIA",
            "CALLE",
            "CARRERA",
            "JIRON",
            "PISO",
            "VITACURA",
            "PROCURAR ORIENTACAO MEDICA",
            "PAIS DE ORIGEM",
            "MADE IN",
            "ORIGIN"
    };

    /**
     * Marcadores fortes usados especificamente durante o fechamento
     * da seção de ingredientes.
     *
     * Mantemos uma lista separada para deixar explícita a intenção
     * desta etapa do parser.
     */
    private static final String[] STRONG_INGREDIENT_SECTION_STOPS = {
            "LOT",
            "CONTEUDO",
            "PESO LIQUIDO",
            "VOLUME",
            "FABRICADO POR",
            "FABRICADO",
            "FABRICANTE",
            "MANUFACTURER",
            "MANUFACTURED BY",
            "MANUFACTURED",
            "PRODUZIDO POR",
            "PRODUZIDO",
            "MANUFACTURADO POR",
            "ELABORADO POR",
            "ELABORADO",
            "IMPORTADO POR",
            "IMPORTADO",
            "IMPORTADOR",
            "IMPORTED BY",
            "IMPORTED",
            "DISTRIBUIDO POR",
            "DISTRIBUIDO",
            "DISTRIBUIDORA",
            "DISTRIBUTOR",
            "DISTRIBUTED BY",
            "CNPJ",
            "CEP",
            "AUTFUNC",
            "AUT FUNC",
            "AUT. FUNC",
            "AUTORIZACAO",
            "REGISTRO ANVISA",
            "NUMERO DE REGISTRO",
            "PROCESSO ANVISA",
            "CODIGO DE BARRAS",
            "CODIGO EAN",
            "EAN",
            "GTIN",
            "LOTE",
            "LQTE",
            "L0TE",
            "BATCH",
            "VALIDADE",
            "VENCIMENTO",
            "EXPIRY",
            "MODO DE USO",
            "MODO DE USAR",
            "MODO DE EMPLEO",
            "INSTRUCOES DE USO",
            "COMO USAR",
            "COMO UTILIZAR",
            "HOW TO USE",
            "DIRECTIONS FOR USE",
            "PRECAUCOES",
            "PRECAUCAO",
            "PRECAUTIONS",
            "CAUTIONS",
            "CUIDADOS",
            "CUIDADO",
            "ADVERTENCIA",
            "ADVERTENCIAS",
            "WARNING",
            "WARNINGS",
            "SAC",
            "ATENDIMENTO AO CONSUMIDOR",
            "CENTRAL DE ATENDIMENTO",
            "WHATSAPP",
            "QUESTIONS",
            "QUESTION",
            "MAIL SUPPORT",
            "MAIL SUPOR",
            "CUSTOMER SERVICE",
            "ATENDIMENTO AO CONSUMIDOR",
            "CONTACT",
            "INDUSTRIA BRASILEIRA",
            "INDUSTRIA BRASILEIRO",
            "NDUSTRIA BRASILEIRA",
            "INDUSTRA BRASILEIRA",
            "INDUSTRIA BRASILENA",
            "PAIS DE ORIGEM",
            "MADE IN",
            "ORIGIN"
    };

    /*
     * ================================================================
     * EMPRESAS
     * ================================================================
     */

    private static final String[] COMPANY_SUFFIXES = {
            "LTDA",
            "S A",
            "S/A",
            "SA",
            "SAS",
            "SRL",
            "S DE RL",
            "S DE R L",
            "LLC",
            "INC",
            "CORP",
            "CORPORATION",
            "PLC",
            "BV",
            "NV",
            "GMBH",
            "AG",
            "KG",
            "SPA",
            "S P A",
            "EIRELI",
            "ME",
            "LTD"
    };

    private static final String[] CORPORATE_MARKERS = {
            "UNILEVER",
            "LOREAL",
            "L OREAL",
            "PROCTER",
            "COLGATE",
            "PALMOLIVE",
            "NIVEA",
            "BEIERSDORF",
            "JOHNSON",
            "RECKITT",
            "HENKEL",
            "KENVUE",
            "LVMH",
            "ESTEE LAUDER",
            "THE BODY SHOP",
            "NATURA",
            "BOTICARIO",
            "GRANADO",
            "CETAPHIL",
            "CERAVE",
            "EUCERIN",
            "DOVE",
            "REVLON",
            "MAYBELLINE",
            "RIMMEL",
            "COTY",
            "SHISEIDO"
    };

    /*
     * ================================================================
     * CLAIMS
     * ================================================================
     */

    private static final String[] CLAIM_TOKENS = {
            "VEGANO",
            "VEGAN",
            "VEGETARIANO",
            "VEGETARIAN",
            "CRUELTY FREE",
            "CRUELTY-FREE",
            "NAO TESTADO EM ANIMAIS",
            "NOT TESTED ON ANIMALS",
            "SEM PARABENOS",
            "PARABEN FREE",
            "SEM SILICONE",
            "SILICONE FREE",
            "SEM SULFATO",
            "SEM SULFATOS",
            "SULFATE FREE",
            "SEM FRAGRANCIA",
            "FRAGRANCE FREE",
            "SEM PERFUME",
            "SEM ALCOOL",
            "ALCOHOL FREE",
            "HIPOALERGENICO",
            "HYPOALLERGENIC",
            "DERMATOLOGICAMENTE TESTADO",
            "DERMATOLOGICAMENTE TESTADA",
            "DERMATOLOGICALLY TESTED",
            "OFTALMOLOGICAMENTE TESTADO",
            "CLINICAMENTE TESTADO",
            "CLINICALLY TESTED",
            "PH BALANCEADO",
            "PH BALANCED",
            "SEM GLUTEN",
            "GLUTEN FREE",
            "SEM OLEO MINERAL",
            "MINERAL OIL FREE",
            "SEM CORANTES",
            "DYE FREE",
            "SEM TALCO",
            "TALC FREE",
            "PROTECAO SOLAR",
            "RESISTENTE A AGUA",
            "WATER RESISTANT",
            "WATERPROOF",
            "PARA PELE SENSIVEL",
            "FOR SENSITIVE SKIN",
            "NATURAL",
            "NATURALE",
            "ORGANICO",
            "ORGANIC",
            "OIL FREE",
            "NAO COMEDOGENICO",
            "NON COMEDOGENIC",
            "TESTADO CLINICAMENTE"
    };

    /*
     * ================================================================
     * PAÍSES
     * ================================================================
     */

    private static final String[] COUNTRY_NAMES = {
            "BRASIL",
            "BRAZIL",
            "ARGENTINA",
            "CHILE",
            "URUGUAI",
            "URUGUAY",
            "PARAGUAI",
            "PARAGUAY",
            "BOLIVIA",
            "PERU",
            "COLOMBIA",
            "EQUADOR",
            "ECUADOR",
            "MEXICO",
            "ESTADOS UNIDOS",
            "UNITED STATES",
            "USA",
            "CANADA",
            "FRANCA",
            "FRANCE",
            "ITALIA",
            "ITALY",
            "ALEMANHA",
            "GERMANY",
            "ESPANHA",
            "SPAIN",
            "PORTUGAL",
            "REINO UNIDO",
            "UNITED KINGDOM",
            "INDIA",
            "CHINA",
            "JAPAO",
            "JAPAN",
            "AUSTRALIA",
            "POLONIA",
            "POLAND",
            "TURQUIA",
            "TURKEY",
            "SUICA",
            "SWITZERLAND",
            "PAISES BAIXOS",
            "NETHERLANDS"
    };

    private static final String[] BRAZIL_ORIGIN_MARKERS = {
            "INDUSTRIA BRASILEIRA",
            "INDUSTRIA BRASILEIRO",
            "INDUSTRIA BRASILEIRA",
            "INDUSTRIA BRASILEÑO",
            "FABRICADO NO BRASIL",
            "FABRICADO NO BRAZIL",
            "MADE IN BRAZIL",
            "MADE IN BRASIL",
            "PAIS DE ORIGEM BRASIL",
            "PAIS DE ORIGEM: BRASIL",
            "ORIGEM BRASIL",
            "ORIGEM: BRASIL",
            "NDUSTRIA BRASILEIRA",
            "INDUSTRA BRASILEIRA",
            "INDUSTRIA 8RASILENA",
            "INDUSTRA 8RASILENA",
            "BRASILEIRAINDUSTRIA"
    };

    /*
     * ================================================================
     * SEGURANÇA
     * ================================================================
     */

    private static final String[] SECURITY_TOKENS = {
            "NAO APLICAR",
            "NO APLICAR",
            "NO EXPONER",
            "PROTEGER",
            "PROTEJA",
            "EVITAR",
            "MANTER FORA",
            "FORA DO ALCANCE",
            "ALCANCE DE CRIANCAS",
            "ALCANCE DE LOS NINOS",
            "SE OCORRER",
            "SE OCORRER ALERGIA",
            "SE APARECER",
            "SINTOMAS DE IRRITACAO",
            "SINTOMAS DE IRRITACION",
            "IRRITACAO",
            "IRRITACION",
            "PRURIDO",
            "ALERGIA",
            "OLHOS",
            "OJOS",
            "FOGO",
            "LLAMA",
            "SUPERFICIES QUENTES",
            "SUPERFICIES CALIENTES",
            "CONTEUDO SOB PRESSAO",
            "CONTENIDO BAJO PRESION",
            "PODE EXPLODIR",
            "NO INCINERAR",
            "NAO INCINERAR",
            "PERIGO",
            "DANGER",
            "PELIGRO",
            "ATENCAO",
            "CAUTION",
            "WARNING"
    };

    /*
     * ================================================================
     * REGEX
     * ================================================================
     */

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile(
                    "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern ADDRESS_INLINE_PATTERN =
            Pattern.compile(
                    "(?i)\\b(?:RUA|R\\.?|AVENIDA|AV\\.?|ALAMEDA|TRAVESSA|RODOVIA|"
                            + "CALLE|CARRERA|JIRON|BOULEVARD|BLVD)\\b"
            );

    private static final Pattern CITY_STATE_PATTERN =
            Pattern.compile(
                    "(?i)\\b[A-ZÁÉÍÓÚÃÕÇ]{3,}"
                            + "(?:\\s+[A-ZÁÉÍÓÚÃÕÇ]{2,}){0,6}"
                            + "\\s*/\\s*[A-Z]{2}\\b"
            );

    private static final Pattern COMPANY_REGISTRATION_FRAGMENT_PATTERN =
            Pattern.compile(
                    "(?i)(?<!\\d)\\d{2,4}\\s*/\\s*[A-Z0-9]{3,8}"
            );

    private static final Pattern ORIGIN_INLINE_PATTERN =
            Pattern.compile(
                    "(?i)\\b(?:HECHO\\s+EN|FEITO\\s+NO|MADE\\s+IN|"
                            + "FABRICADO\\s+NO|ORIGEM)\\b"
            );

    /**
     * Captura um pequeno bloco que termina claramente em sufixo empresarial.
     * Usado apenas como barreira de segurança dentro da composição,
     * principalmente quando o OCR cola empresa + endereço na mesma linha.
     */
    /** OCR contact text that may be malformed. */
    private static final Pattern CONTACT_CONTEXT_PATTERN =
            Pattern.compile(
                    "(?i)\\b(?:QUESTIONS{0,2}|CONTACT|CUSTOMER\\s+SERVICE|"
                            + "ATENDIMENTO(?:\\s+AO\\s+CONSUMIDOR)?|"
                            + "MAIL\\s+SUPPORT|MAIL\\s+SUPOR|MAL\\s+SOPPORT|"
                            + "SOPPORT|SUPPORI|SUPPOR|E[- ]?MAIL)"
                            + "[^\\n]{0,180}?"
                            + "(?:@|MAIL|MAL|E[- ]?MAIL|SUPPORT|SOPPORT|SUPPORI|SUPPOR|CALL|PHONE|FONE)"
                            + "[^\\n]{0,140}"
            );

    /**
     * V7.1: preserva o texto que sobra depois de um heading de contato.
     * O OCR pode reconhecer o e-mail de forma incompleta, portanto este
     * padrão não exige que o endereço tenha um domínio válido.
     */
    private static final Pattern CONTACT_RESIDUAL_PATTERN =
            Pattern.compile(
                    "(?i)\\b(?:QUESTIONS?|CONTACT|CUSTOMER\\s+SERVICE|"
                            + "ATENDIMENTO(?:\\s+AO\\s+CONSUMIDOR)?|"
                            + "MAIL\\s+SUPPORT|MAIL\\s+SUPOR|MAL\\s+SOPPORT|"
                            + "SOPPORT|SUPPORI|SUPPOR|E[- ]?MAIL)\\b"
                            + "\\s*[:;,.\\-]*\\s*(.+)"
            );

    private static final Pattern INGREDIENT_HEADING_PREFIX_OCR_PATTERN =
            Pattern.compile(
                    "(?i)^(?:N?GREDIENTES(?:INGREDIENTS|INGREDIENTES)?|"
                            + "INGREDIENTES(?:INGREDIENTS)?|"
                            + "INGREDIENTS(?:INGREDIENTES)?)"
                            + "\\s*[:;.,#\\-]*\\s*"
            );

    private static final Pattern USAGE_INGREDIENT_START_PATTERN =
            Pattern.compile(
                    "(?i)(?:N?GREDIENTES(?:INGREDIENTS)?|"
                            + "INGREDIENTES(?:INGREDIENTS)?|"
                            + "INGREDIENTS(?:INGREDIENTES)?)"
            );

    private static final Pattern INLINE_COMPANY_PATTERN =
            Pattern.compile(
                    "(?i)\\b(?:(?:\\p{L})[\\p{L}0-9'&.-]{1,}\\s+){1,4}"
                            + "(?:LTDA|LTD|S\\.?A\\.?|S/A|SA|SAS|SRL|LLC|INC|CORP|"
                            + "CORPORATION|PLC|BV|NV|GMBH|AG|KG|SPA|EIRELI|ME)\\b"
            );

    private static final Pattern URL_PATTERN =
            Pattern.compile(
                    // Bare domains need a conservative suffix; OCR dots are not URLs.
                    "(?i)(?<![a-z0-9@._-])(?:"
                            + "(?:https?://|www\\.)[a-z0-9][a-z0-9.-]*"
                            + "|(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+"
                            + "(?:com|org|net|edu|gov|br|pt|uk|us|fr|de|it|es|eu)"
                            + ")(?![a-z0-9_-]|\\.[a-z])"
                            + "(?:/[a-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]*)?"
            );

    private static final Pattern PHONE_PATTERN =
            Pattern.compile(
                    "(?<!\\d)"
                            + "(?:\\+?\\d{1,3}[ .-]?)?"
                            + "(?:\\(?\\d{2}\\)?[ .-]?)?"
                            + "(?:9?\\d{4})[ .-]?\\d{4}"
                            + "(?!\\d)"
            );

    private static final Pattern SAC_PHONE_PATTERN =
            Pattern.compile(
                    "(?<!\\d)"
                            + "(?:0800|0300)\\s*\\d{3,4}[ .-]?\\d{4}"
                            + "(?!\\d)"
            );

    private static final Pattern CNPJ_PATTERN =
            Pattern.compile(
                    "(?<!\\d)"
                            + "\\d{2}[ .-]?\\d{3}[ .-]?\\d{3}"
                            + "[\\/. -]?\\d{4}[\\- .]?\\d{2}"
                            + "(?!\\d)"
            );

    private static final Pattern CEP_PATTERN =
            Pattern.compile(
                    "(?<!\\d)"
                            + "\\d{5}[ .-]?\\d{3}"
                            + "(?!\\d)"
            );

    private static final Pattern REGISTRATION_MARKER_PATTERN =
            Pattern.compile(
                    "(?i)\\b(?:REGISTRO(?:\\s+ANVISA)?"
                            + "|REGISTRATION"
                            + "|PROCESSO(?:\\s+ANVISA)?"
                            + "|AUT\\s*FUNC"
                            + "|AUT\\.?\\s*FUNC"
                            + "|AUTL?FUNC"
                            + "|AUTORIZACAO"
                            + "|NUMERO\\s+DE\\s+REGISTRO)\\b"
            );

    private static final Pattern REGISTRATION_VALUE_PATTERN =
            Pattern.compile(
                    "(?<![A-Z0-9])"
                            + "(?:"
                            + "2\\s*\\d{3,7}(?:\\s+\\d{1,3}(?:-[A-Z0-9]{1,8})?)?"
                            + "|2\\s*\\d{4,7}(?:-\\d{1,4})?"
                            + "|\\d{1,3}\\.\\d{3,7}(?:-\\d{1,4})?"
                            + ")"
                            + "(?![A-Z0-9])"
            );

    private static final Pattern NET_CONTENT_PATTERN =
            Pattern.compile(
                    "(?i)(?<![A-Z0-9])"
                            + "(?:CONTEUDO(?:\\s+LIQUIDO)?"
                            + "|PESO(?:\\s+LIQUIDO)?"
                            + "|VOLUME)?"
                            + "\\s*[:#.;-]?\\s*"
                            + "(\\d+(?:[.,]\\d+)?)\\s*"
                            + "(ML|MI|M1|L|CL|DL|UL|G|GR|KG|MG|OZ|FL\\s*OZ)\\b"
            );

    private static final Pattern GENERIC_SIZE_PATTERN =
            Pattern.compile(
                    "(?i)(?<![A-Z0-9])"
                            + "(\\d+(?:[.,]\\d+)?)\\s*"
                            + "(ML|MI|M1|L|CL|DL|UL|G|GR|KG|MG|OZ|FL\\s*OZ)\\b"
            );

    private static final Pattern BARCODE_PATTERN =
            Pattern.compile(
                    "(?<!\\d)(\\d{8}|\\d{12}|\\d{13}|\\d{14})(?!\\d)"
            );

    private static final Pattern STREET_PATTERN =
            Pattern.compile(
                    "(?i)\\b(?:RUA|AVENIDA|AV|ALAMEDA|TRAVESSA|"
                            + "RODOVIA|CALLE|CARRERA|JIRON|BOULEVARD|BLVD)\\b"
            );

    private static final Pattern NUMBERED_ADDRESS_PATTERN =
            Pattern.compile(
                    "(?i)\\b(?:[A-ZÁÉÍÓÚÃÕÇ]"
                            + "[A-ZÁÉÍÓÚÃÕÇ0-9 .'-]{2,60})"
                            + "\\s+\\d{1,6}\\b"
            );

    /*
     * ================================================================
     * INCI CONHECIDOS
     * ================================================================
     */

    private static final Set<String> COMMON_INCI_BOUNDARIES =
            new LinkedHashSet<>(
                    Arrays.asList(
                            "AQUA",
                            "WATER",
                            "GLYCERIN",
                            "PARFUM",
                            "FRAGRANCE",
                            "ALCOHOL",
                            "PROPANEDIOL",
                            "BUTANE",
                            "ISOBUTANE",
                            "PROPANE",
                            "DIMETHICONE",
                            "DIMETHICONOL",
                            "CARBOMER",
                            "TOCOPHEROL",
                            "NIACINAMIDE",
                            "PANTHENOL",
                            "SODIUM CHLORIDE",
                            "SODIUM HYDROXIDE",
                            "CITRIC ACID",
                            "LACTIC ACID",
                            "SALICYLIC ACID",
                            "BENZYL ALCOHOL",
                            "BENZYL SALICYLATE",
                            "LINALOOL",
                            "LIMONENE",
                            "HEXYL CINNAMAL",
                            "ALPHA-ISOMETHYL IONONE",
                            "COUMARIN",
                            "CITRONELLOL",
                            "GERANIOL",
                            "CITRAL",
                            "EUGENOL",
                            "SODIUM BENZOATE",
                            "POTASSIUM SORBATE",
                            "PHENOXYETHANOL",
                            "DISODIUM EDTA",
                            "MICA",
                            "TITANIUM DIOXIDE",
                            "IRON OXIDES",
                            "ZINC OXIDE",
                            "TALC",
                            "SILICA",
                            "KAOLIN",
                            "ALUMINA",
                            "PROPYLENE CARBONATE",
                            "CAPRYLYL GLYCOL",
                            "CETEARYL ALCOHOL",
                            "CETYL ALCOHOL",
                            "STEARYL ALCOHOL",
                            "GLYCERYL STEARATE",
                            "POLYQUATERNIUM-10",
                            "BEHENTRIMONIUM CHLORIDE",
                            "CETRIMONIUM CHLORIDE",
                            "SODIUM LAURYL SULFATE",
                            "SODIUM LAURETH SULFATE",
                            "COCAMIDOPROPYL BETAINE",
                            "COCO-GLUCOSIDE",
                            "DECYL GLUCOSIDE",
                            "SODIUM PCA",
                            "ARGININE",
                            "BIOTIN",
                            "CAFFEINE",
                            "SQUALANE",
                            "CINNAMAL",
                            "DISTEARDIMONIUM HECTORITE",
                            "PROPYLENE GLYCOL",
                            "BUTYLENE GLYCOL",
                            "CAPRYLIC/CAPRIC TRIGLYCERIDE",
                            "C12-15 ALKYL BENZOATE",
                            "ISOPROPYL MYRISTATE",
                            "ALUMINUM SESQUICHLOROHYDRATE",
                            "PPG-14 BUTYL ETHER",
                            "QUATERNIUM-18 HECTORITE",
                            "2-METHYL-5-CYCLOHEXYLPENTANOL",
                            "TOCOPHERYL ACETATE",
                            "GOSSYPIUM HERBACEUM SEED OIL",
                            "BUTYROSPERMUM PARKII BUTTER",
                            "MACADAMIA TERNIFOLIA SEED OIL",
                            "COCOS NUCIFERA FRUIT EXTRACT",
                            "CHAMOMILLA RECUTITA FLOWER EXTRACT",
                            "ALOE BARBADENSIS LEAF EXTRACT",
                            "CAMELLIA SINENSIS LEAF EXTRACT",
                            "CINNAMOMUM ZEYLANICUM BARK EXTRACT",
                            "COMMIPHORA EUROPAEA FRUIT OIL",
                            "ARGANIA SPINOSA KERNEL OIL",
                            "MYRRHA RESIN EXTRACT",
                            "OLEA",
                            "LECITHIN",
                            "POLYGLYCERYL-3 DIISOSTEARATE",
                            "HYDROXYCITRONELLAL",
                            "PROPYLENE CARBONATE",
                            "BENZYL SALICYLATE"
                    )
            );

    /*
     * ================================================================
     * LIXO ADMINISTRATIVO
     * ================================================================
     */

    private static final String[] ADMIN_GARBAGE = {
            "CNPJ",
            "CEP",
            "AUTFUNC",
            "AUT FUNC",
            "AUTORIZACAO",
            "REGISTRO",
            "PROCESSO",
            "LOTE",
            "LQTE",
            "L0TE",
            "BATCH",
            "VALIDADE",
            "VENCIMENTO",
            "EXPIRY",
            "SAC",
            "ATENDIMENTO",
            "CONSUMIDOR",
            "WHATSAPP",
            "IMPORTADO",
            "DISTRIBUIDO",
            "FABRICADO",
            "INDUSTRIA BRASILEIRA",
            "INDUSTRIA",
            "SARGENTO",
            "RUA",
            "AVENIDA",
            "CALLE",
            "CARRERA",
            "PISO",
            "PAIS DE ORIGEM",
            "MADE IN",
            "WWW.",
            "HTTP",
            "HTTPS",
            "LTDA",
            "LTD",
            "S/A",
            "S A",
            "SAS",
            "SRL",
            "LLC",
            "INC",
            "CORP",
            "CORPORATION",
            "PLC",
            "GMBH",
            "EIRELI"
    };

    /*
     * Limite de segurança.
     *
     * Não significa que uma composição real necessariamente tenha
     * este tamanho. É apenas uma proteção contra OCR contaminado ou
     * uma captura em que nenhum marcador administrativo foi reconhecido.
     */
    private static final int MAX_INGREDIENT_SECTION_LINES =
            64;

    private ScanBackExtractor() {
    }

    /*
     * ================================================================
     * ENTRADAS PÚBLICAS
     * ================================================================
     */

    @NonNull
    public static ScanBackData extract(
            @NonNull ScanOcrResult ocr
    ) {
        return extract(
                ocr.getFullText(),
                ocr.getLines(),
                ocr.getTokens()
        );
    }

    @NonNull
    public static ScanBackData extract(
            @NonNull List<String> ocrLines
    ) {
        String full =
                joinLines(
                        ocrLines
                );

        return extract(
                full,
                ocrLines
        );
    }

    @NonNull
    public static ScanBackData extract(
            @NonNull String fullText,
            @NonNull List<String> sourceLines
    ) {
        return extract(
                fullText,
                sourceLines,
                Collections.emptyList()
        );
    }

    @NonNull
    public static ScanBackData extract(
            @NonNull String fullText,
            @NonNull List<String> sourceLines,
            @NonNull List<ScanOcrToken> spatialTokens
    ) {

        List<String> originalLines =
                normalizarLinhas(
                        sourceLines
                );

        List<String> lines = originalLines;
        List<ScanOcrToken> orderedTokens = new ArrayList<>();
        List<List<ScanOcrToken>> visualRows = ScanBackSpatialLayout.rows(sourceLines, spatialTokens);
        if (!visualRows.isEmpty()) {
            lines = new ArrayList<>();
            for (int rowIndex = 0; rowIndex < visualRows.size(); rowIndex++) {
                List<ScanOcrToken> row = visualRows.get(rowIndex);
                lines.add(ScanBackSpatialLayout.join(row));
                for (int element = 0; element < row.size(); element++) {
                    ScanOcrToken t = row.get(element);
                    orderedTokens.add(new ScanOcrToken(t.getText(), t.getBoundingBox(),
                            t.getConfidence(), t.getRotation(), t.getBlockIndex(), rowIndex, element));
                }
            }
        }
        Log.d(TAG, "BACK_LAYOUT=" + (visualRows.isEmpty() ? "TEXT_FALLBACK" : "GEOMETRIC_ROWS")
                + " rows=" + lines.size());
        for (int i = 0; i < lines.size(); i++) {
            Log.d(TAG, "BACK_LINE[" + i + "]: " + lines.get(i));
        }

        String normalizedFullText =
                compactarEspacos(
                        normalizar(
                                fullText
                        )
                );

        /*
         * Mantemos esta variável por compatibilidade e diagnóstico.
         * O parser trabalha principalmente com as linhas originais
         * normalizadas, porque a estrutura de linhas é importante.
         */
        if (normalizedFullText.isEmpty()) {
            Log.d(
                    TAG,
                    "TEXTO COMPLETO NORMALIZADO: vazio"
            );
        }

        IngredientSection section =
                localizarSecaoIngredientes(
                        lines
                );

        List<ScanBackIngredientCandidate> ingredients =
                extrairIngredientes(
                        section,
                        orderedTokens
                );

        Log.d(
                TAG,
                "SPATIAL OCR: tokens="
                        + (spatialTokens == null
                        ? 0
                        : spatialTokens.size())
        );

        String manufacturer =
                extrairFabricante(
                        lines
                );

        String address =
                extrairEndereco(
                        lines,
                        manufacturer
                );

        String contact =
                extrairContato(
                        lines
                );

        String country =
                extrairPais(
                        lines,
                        manufacturer
                );

        String batch =
                extrairLote(
                        lines
                );

        String registration =
                extrairRegistro(
                        lines
                );

        String netContent =
                extrairConteudo(
                        lines
                );

        String usage =
                extrairUso(
                        lines,
                        section
                );

        String precautions =
                extrairSeguranca(
                        lines,
                        false,
                        section
                );

        String warnings =
                extrairSeguranca(
                        lines,
                        true,
                        section
                );

        List<String> claims =
                extrairClaims(
                        lines
                );

        String barcode =
                extrairBarcode(
                        lines
                );

        String otherText =
                extrairOtherText(
                        lines,
                        section
                );

        String signature =
                gerarAssinaturaOCR(
                        ingredients
                );

        /*
         * ============================================================
         * LOG
         * ============================================================
         */

        Log.d(
                TAG,
                "========================================"
        );

        Log.d(
                TAG,
                "V7.1.2 PARSER / BACK SCAN"
        );

        Log.d(
                TAG,
                "========================================"
        );

        Log.d(
                TAG,
                "SECAO INGREDIENTES ENCONTRADA: "
                        + section.found
        );

        Log.d(
                TAG,
                "INGREDIENTES START: "
                        + section.startIndex
        );

        Log.d(
                TAG,
                "INGREDIENTES END: "
                        + section.endIndex
        );

        Log.d(
                TAG,
                "INGREDIENTES STOP: "
                        + section.stopReason
        );

        Log.d(
                TAG,
                "INGREDIENTES RAW: "
                        + section.rawText
        );

        Log.d(
                TAG,
                "QUANTIDADE INGREDIENTES: "
                        + ingredients.size()
        );

        Log.d(
                TAG,
                "FABRICANTE: "
                        + manufacturer
        );

        Log.d(
                TAG,
                "ENDERECO: "
                        + address
        );

        Log.d(
                TAG,
                "CONTATO: "
                        + contact
        );

        Log.d(
                TAG,
                "PAIS: "
                        + country
        );

        Log.d(
                TAG,
                "LOTE: "
                        + batch
        );

        Log.d(
                TAG,
                "REGISTRO: "
                        + registration
        );

        Log.d(
                TAG,
                "CONTEUDO: "
                        + netContent
        );

        Log.d(
                TAG,
                "BARCODE: "
                        + barcode
        );

        Log.d(
                TAG,
                "USO: "
                        + usage
        );

        Log.d(
                TAG,
                "PRECAUCOES: "
                        + precautions
        );

        Log.d(
                TAG,
                "WARNINGS: "
                        + warnings
        );

        Log.d(
                TAG,
                "CLAIMS: "
                        + claims
        );

        Log.d(
                TAG,
                "FORMULA SIGNATURE: "
                        + signature
        );

        for (
                ScanBackIngredientCandidate candidate
                : ingredients
        ) {

            Log.d(
                    TAG,
                    "INGREDIENTE "
                            + candidate.getPosition()
                            + " RAW=["
                            + candidate.getRawName()
                            + "]"
                            + " NORMALIZED=["
                            + candidate.getNormalizedName()
                            + "]"
                            + " STATUS="
                            + candidate.getStatus()
            );
        }

        /*
         * ============================================================
         * OBJETO FINAL
         * ============================================================
         */

        return new ScanBackData(
                fullText == null
                        ? EMPTY
                        : fullText,

                originalLines,

                section.found,

                section.rawText,

                ingredients,

                manufacturer,

                address,

                contact,

                country,

                batch,

                registration,

                netContent,

                usage,

                precautions,

                warnings,

                claims,

                barcode,

                otherText,

                signature
        );
    }

    /*
     * ================================================================
     * INGREDIENTES
     * ================================================================
     */

    private static IngredientSection localizarSecaoIngredientes(
            List<String> lines
    ) {

        int start = -1;
        int headingEndColumn = 0;
        String heading = EMPTY;
        boolean detectedByContent = false;

        /*
         * ============================================================
         * 1) TENTATIVA NORMAL
         * ============================================================
         */

        for (
                int i = 0;
                i < lines.size();
                i++
        ) {

            String line =
                    lines.get(i);

            HeadingMatch match =
                    encontrarHeadingIngredientesRobusto(
                            line
                    );

            if (match != null) {

                start =
                        i;

                headingEndColumn =
                        Math.min(
                                line.length(),
                                match.endInOriginal
                        );

                heading =
                        match.term;

                break;
            }
        }

        /*
         * ============================================================
         * 2) FALLBACK POR CONTEÚDO INCI
         * ============================================================
         *
         * Caso o OCR destrua completamente "INGREDIENTES".
         *
         * Exemplo real:
         *
         * UENTES: BUTANE: ISOBUTANE: PROPANE...
         */

        if (start < 0) {

            for (
                    int i = 0;
                    i < lines.size();
                    i++
            ) {

                if (
                        linhaPareceInicioDeIngredientes(
                                lines,
                                i
                        )
                ) {

                    start =
                            i;

                    headingEndColumn =
                            0;

                    heading =
                            "INCI_CONTENT_FALLBACK";

                    detectedByContent =
                            true;

                    break;
                }
            }
        }

        if (start < 0) {

            return new IngredientSection(
                    false,
                    -1,
                    -1,
                    EMPTY,
                    "NOT_FOUND"
            );
        }

        List<String> collected =
                new ArrayList<>();

        /*
         * ============================================================
         * PRIMEIRA LINHA
         * ============================================================
         */

        String firstLine =
                lines.get(start);

        String firstRemainder;

        if (detectedByContent) {

            /*
             * Quando o heading não foi reconhecido, tentamos cortar
             * tudo antes do primeiro INCI conhecido.
             */
            firstRemainder =
                    cortarPrefixoAntesDoPrimeiroINCI(
                            firstLine
                    );

            firstRemainder =
                    removerPrefixoHeadingIngredientesOCR(
                            firstRemainder
                    );

        } else {

            firstRemainder =
                    substringSafe(
                            firstLine,
                            headingEndColumn
                    );

            firstRemainder =
                    limparRestoHeading(
                            firstRemainder
                    );
        }

        String firstStopReason =
                null;

        if (
                !firstRemainder.isEmpty()
        ) {

            /*
             * A primeira linha é um caso especial: quando o OCR coloca
             * \"INGREDIENTES: ... MADE IN BRASIL\" ou \"... Ltda. Av\"
             * na mesma linha do heading, o loop das linhas seguintes não
             * chega a executar detectarFimIngredientes().
             *
             * Reutilizamos a mesma lógica aqui, sem criar um segundo
             * parser, para manter o comportamento atual.
             */
            List<String> seedContent =
                    Collections.singletonList(
                            firstRemainder
                    );

            StopMatch firstStop =
                    detectarFimIngredientes(
                            firstRemainder,
                            seedContent
                    );

            if (firstStop != null) {

                String prefix =
                        extrairPrefixoAntesDoStop(
                                firstRemainder,
                                firstStop
                        );

                firstRemainder =
                        prefix;

                firstStopReason =
                        firstStop.reason;
            }

            if (!firstRemainder.isEmpty()) {

                collected.add(
                        limparLinhaIngredientes(
                                firstRemainder
                        )
                );
            }
        }

        int end =
                start;

        String stopReason =
                firstStopReason == null
                        ? "END_OF_TEXT"
                        : firstStopReason;

        /*
         * ============================================================
         * DEMAIS LINHAS
         * ============================================================
         */

        for (
                int i = start + 1;
                firstStopReason == null && i < lines.size();
                i++
        ) {

            if (
                    i - start
                            > MAX_INGREDIENT_SECTION_LINES
            ) {

                stopReason =
                        "MAX_INGREDIENT_SECTION_LINES";

                end =
                        i - 1;

                break;
            }

            String line =
                    lines.get(i);

            if (
                    line == null
                            || line.trim().isEmpty()
            ) {
                continue;
            }

            StopMatch stop =
                    detectarFimIngredientes(
                            line,
                            collected
                    );

            if (stop != null) {

                String prefix =
                        extrairPrefixoAntesDoStop(
                                line,
                                stop
                        );

                if (!prefix.isEmpty()) {

                    collected.add(
                            limparLinhaIngredientes(
                                    prefix
                            )
                    );
                }

                stopReason =
                        stop.reason;

                end =
                        i;

                break;
            }

            String clean =
                    limparLinhaIngredientes(
                            line
                    );

            if (!clean.isEmpty()) {

                collected.add(
                        clean
                );

                end =
                        i;
            }
        }

        String raw =
                compactarEspacos(
                        String.join(
                                " ",
                                collected
                        )
                );

        raw =
                limparPontosFinais(
                        raw
                );

        Log.d(
                TAG,
                "Secao ingredientes: heading="
                        + heading
                        + " start="
                        + start
                        + " end="
                        + end
                        + " found=true stop="
                        + stopReason
                        + " fallback="
                        + detectedByContent
        );

        return new IngredientSection(
                true,
                start,
                end,
                raw,
                stopReason
        );
    }

    private static HeadingMatch encontrarHeadingIngredientesRobusto(
            String line
    ) {

        if (
                line == null
                        || line.trim().isEmpty()
        ) {
            return null;
        }

        String normalized =
                normalizar(
                        line
                );

        /*
         * 1) Correspondência exata.
         */
        HeadingMatch exact =
                encontrarHeading(
                        normalized,
                        INGREDIENT_HEADINGS,
                        false
                );

        if (exact != null) {
            return exact;
        }

        /*
         * 2) Correção de confusões comuns do OCR.
         *
         * Só usamos isso para heading.
         */
        String ocrAdjusted =
                normalizarHeadingOCR(
                        normalized
                );

        exact =
                encontrarHeading(
                        ocrAdjusted,
                        INGREDIENT_HEADINGS,
                        false
                );

        if (exact != null) {
            return exact;
        }

        /*
         * 3) Fuzzy mais tolerante somente para headings.
         */
        String[] tokens =
                normalized.split(
                        "\\s+"
                );

        for (
                String token
                : tokens
        ) {

            // Fuzzy headings must be at the start; punctuation is not part of the word.
            if (!token.equals(tokens[0])) break;
            token = token.replaceAll("^[^A-Z]+|[^A-Z]+$", "");

            if (
                    token.length() < 6
            ) {
                continue;
            }

            for (
                    String heading
                    : INGREDIENT_HEADINGS
            ) {

                String h =
                        normalizar(
                                heading
                        );

                /*
                 * Só fazemos fuzzy em headings de uma palavra.
                 */
                if (
                        h.contains(" ")
                ) {
                    continue;
                }

                int distance =
                        levenshtein(
                                token,
                                h
                        );

                int allowed =
                        h.length() >= 10
                                ? 3
                                : 2;

                if (
                        distance <= allowed
                ) {

                    int idx =
                            normalized.indexOf(
                                    token
                            );

                    if (idx >= 0) {

                        return new HeadingMatch(
                                heading,
                                idx + token.length()
                        );
                    }
                }
            }
        }

        return null;
    }

    private static String normalizarHeadingOCR(
            String value
    ) {

        if (value == null) {
            return EMPTY;
        }

        /*
         * Essas substituições só são aplicadas na tentativa
         * de reconhecer o heading.
         */
        return value
                .replace(
                        '0',
                        'O'
                )
                .replace(
                        '1',
                        'I'
                )
                .replace(
                        '5',
                        'S'
                )
                .replace(
                        '8',
                        'B'
                )
                .replaceAll(
                        "[^A-Z ]",
                        " "
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    private static boolean linhaPareceInicioDeIngredientes(
            List<String> lines,
            int index
    ) {

        if (
                lines == null
                        || index < 0
                        || index >= lines.size()
        ) {
            return false;
        }

        String current =
                normalizar(
                        lines.get(index)
                );

        if (current.isEmpty()) {
            return false;
        }

        /*
         * Nunca inicia composição em linha claramente administrativa.
         */
        if (
                pareceAdministrativoForte(
                        current
                )
        ) {
            return false;
        }

        int currentSignals =
                contarSinaisINCI(
                        current
                );

        // Look-ahead must not pull a warning/marketing line into the section.
        if (currentSignals == 0) return false;

        /*
         * Caso muito forte:
         *
         * BUTANE, ISOBUTANE, PROPANE
         */
        if (
                currentSignals >= 2
        ) {
            return true;
        }

        /*
         * Caso a composição esteja distribuída:
         *
         * AQUA
         * GLYCERIN
         * PARFUM
         */
        int consecutiveSignals =
                currentSignals > 0
                        ? 1
                        : 0;

        for (
                int i = index + 1;
                i < Math.min(
                        lines.size(),
                        index + 4
                );
                i++
        ) {

            String next =
                    normalizar(
                            lines.get(i)
                    );

            if (
                    next.isEmpty()
            ) {
                continue;
            }

            if (
                    pareceAdministrativoForte(
                            next
                    )
            ) {
                break;
            }

            int signals =
                    contarSinaisINCI(
                            next
                    );

            if (
                    signals > 0
            ) {

                consecutiveSignals++;
            } else {
                break;
            }
        }

        return consecutiveSignals >= 2;
    }

    private static int contarSinaisINCI(
            String line
    ) {

        if (
                line == null
                        || line.trim().isEmpty()
        ) {
            return 0;
        }

        String n =
                normalizar(
                        line
                );

        int count =
                0;

        /*
         * INCI já conhecidos pelo parser.
         */
        for (
                String known
                : COMMON_INCI_BOUNDARIES
        ) {

            if (
                    contémTermoComFronteira(
                            n,
                            known
                    )
            ) {

                count++;
            }
        }

        /*
         * Alguns padrões químicos comuns.
         */
        String[] chemicalPatterns = {
                "\\b[A-Z]{4,}-[A-Z0-9-]{2,}\\b",
                "\\b[A-Z]{6,}(?:OL|ONE|ATE|IDE|IUM|IC|YL)\\b",
                "\\bCI\\s*\\d{3,6}\\b"
        };

        for (
                String regex
                : chemicalPatterns
        ) {

            if (
                    Pattern.compile(
                            regex
                    ).matcher(
                            n
                    ).find()
            ) {

                count++;
            }
        }

        return Math.min(
                count,
                5
        );
    }

    private static String cortarPrefixoAntesDoPrimeiroINCI(
            String line
    ) {

        if (
                line == null
                        || line.trim().isEmpty()
        ) {
            return EMPTY;
        }

        String normalized =
                normalizar(
                        line
                );

        int bestIndex =
                Integer.MAX_VALUE;

        String bestTerm =
                null;

        /*
         * Primeiro procura INCI conhecido.
         */
        for (
                String known
                : COMMON_INCI_BOUNDARIES
        ) {

            String k =
                    normalizar(
                            known
                    );

            int index =
                    indexOfMarkerWithBoundary(
                            normalized,
                            k
                    );

            if (
                    index >= 0
                            && index < bestIndex
            ) {

                bestIndex =
                        index;

                bestTerm =
                        known;
            }
        }

        if (
                bestIndex
                        != Integer.MAX_VALUE
        ) {

            /*
             * Precisamos retornar uma posição correspondente
             * na linha original.
             */
            int originalIndex =
                    localizarIndiceOriginalPorTextoComFronteira(
                            line,
                            bestTerm
                    );

            if (originalIndex < 0) {
                originalIndex =
                        localizarIndiceAproximadoOriginal(
                                line,
                                bestTerm
                        );
            }

            if (
                    originalIndex >= 0
                            && originalIndex < line.length()
            ) {

                return line.substring(
                        originalIndex
                ).trim();
            }
        }

        /*
         * Caso não tenhamos encontrado INCI conhecido,
         * simplesmente preservamos a linha inteira.
         */
        return removerPrefixoHeadingIngredientesOCR(
                line.trim()
        );
    }

    private static String removerPrefixoHeadingIngredientesOCR(
            String line
    ) {
        if (line == null || line.trim().isEmpty()) {
            return EMPTY;
        }

        String value = normalizarOCRBasico(line).trim();
        Matcher matcher = INGREDIENT_HEADING_PREFIX_OCR_PATTERN.matcher(value);
        if (matcher.find()) {
            return value.substring(matcher.end()).trim();
        }
        value = value.replaceFirst(
                "(?i)^N?GREDIENTESINGREDIENTS\\s*[:;.,#\\-]*\\s*",
                EMPTY
        );
        value = value.replaceFirst(
                "(?i)^INGREDIENTESINGREDIENTS\\s*[:;.,#\\-]*\\s*",
                EMPTY
        );
        return value.trim();
    }

    private static StopMatch detectarFimIngredientes(
            String line,
            List<String> collected
    ) {

        if (
                line == null
                        || line.trim().isEmpty()
        ) {
            return null;
        }

        String normalized =
                normalizar(
                        line
                );

        if (normalized.isEmpty()) {
            return null;
        }

        Matcher safety = Pattern.compile(
                "(?i)\\b(?:PROCURAR\\s+ORIENTA[ÇC][ÃA]O\\s+M[EÉ]DICA|"
                        + "N[ÃA]O\\s+CONT[EÉ]M\\s+CFC|(?:MAIS|M[AÁ]S)\\s+VENDIDO|"
                        + "MERCADO\\s+DE\\s+DESODORANTES|ALERGIA|PRURIDO|"
                        + "IRRITA[ÇC][ÃA]O|VIDE\\s+FUNDO)\\b").matcher(line);
        if (safety.find()) {
            String prefix = line.substring(0, safety.start());
            StopMatch explicitSafety = encontrarPrimeiroStop(line, STRONG_INGREDIENT_SECTION_STOPS);
            int start = explicitSafety == null ? safety.start()
                    : Math.min(safety.start(), explicitSafety.positionInLine);
            if (!linhaComecaComInciConhecido(prefix)
                    && encontrarHeadingIngredientesRobusto(prefix) == null) start = 0;
            return new StopMatch("SAFETY_OR_MARKETING", start);
        }

        /*
         * ============================================================
         * 1) MARCADORES ADMINISTRATIVOS EXPLÍCITOS
         * ============================================================
         */

        StopMatch explicit =
                encontrarPrimeiroStop(
                        line,
                        STRONG_INGREDIENT_SECTION_STOPS
                );

        if (explicit != null) {

            if (
                    jaTemConteudoIngrediente(
                            collected
                    )
                            || ehStopSempreForte(
                            explicit.reason
                    )
            ) {

                return explicit;
            }
        }

        /*
         * ============================================================
         * 2) ORIGEM DENTRO DA MESMA LINHA
         * ============================================================
         *
         * Exemplo real:
         *
         * ... PEEL OIL (CITRUS LIMON) ...
         * - São José dos Pinhais/PR - Brasil
         * ... Made in Brasil
         */

        Matcher origin =
                ORIGIN_INLINE_PATTERN.matcher(
                        line
                );

        if (origin.find()) {

            return new StopMatch(
                    origin.group(),
                    origin.start()
            );
        }

        /*
         * ============================================================
         * 3) ENDEREÇO DENTRO DA MESMA LINHA
         * ============================================================
         *
         * Exemplo:
         *
         * ... Ltda., Av. Rui Barbosa, 007/0001-57
         */

        Matcher address =
                ADDRESS_INLINE_PATTERN.matcher(
                        line
                );

        if (
                address.find()
                        && jaTemConteudoIngrediente(
                        collected
                )
        ) {

            int cut =
                    localizarInicioEmpresaAntesDaBarreira(
                            line,
                            address.start()
                    );

            if (cut >= 0) {
                return new StopMatch(
                        "COMPANY_INLINE",
                        cut
                );
            }

            return new StopMatch(
                    address.group(),
                    address.start()
            );
        }

        /*
         * ============================================================
         * 4) EMPRESA COLADA AO FINAL DA COMPOSIÇÃO
         * ============================================================
         *
         * Caso o OCR não preserve nem mesmo o endereço, ainda podemos
         * encontrar algo como:
         *
         * ... PEEL OIL cial Farmacêutica Ltda
         *
         * O sufixo empresarial é usado apenas como barreira final.
         */

        Matcher inlineCompany =
                INLINE_COMPANY_PATTERN.matcher(
                        line
                );

        if (
                inlineCompany.find()
                        && jaTemConteudoIngrediente(
                        collected
                )
        ) {
            return new StopMatch(
                    "COMPANY_INLINE",
                    inlineCompany.start()
            );
        }

        /*
         * ============================================================
         * 5) MUNICÍPIO / UF
         * ============================================================
         *
         * Exemplo:
         *
         * São José dos Pinhais/PR
         */

        Matcher cityState =
                CITY_STATE_PATTERN.matcher(
                        line
                );

        if (
                cityState.find()
                        && jaTemConteudoIngrediente(
                        collected
                )
        ) {

            return new StopMatch(
                    "CITY_STATE",
                    cityState.start()
            );
        }

        /*
         * ============================================================
         * 5) FRAGMENTO DE CNPJ
         * ============================================================
         */

        Matcher companyRegistration =
                COMPANY_REGISTRATION_FRAGMENT_PATTERN.matcher(
                        line
                );

        if (
                companyRegistration.find()
                        && jaTemConteudoIngrediente(
                        collected
                )
        ) {

            return new StopMatch(
                    "COMPANY_REGISTRATION",
                    companyRegistration.start()
            );
        }

        /*
         * ============================================================
         * 6) REGISTRO / PROCESSO
         * ============================================================
         */

        Matcher registrationMarker =
                REGISTRATION_MARKER_PATTERN.matcher(
                        normalized
                );

        if (registrationMarker.find()) {

            return new StopMatch(
                    registrationMarker.group(),
                    registrationMarker.start()
            );
        }

        /*
         * ============================================================
         * 7) CNPJ / CEP
         * ============================================================
         */

        Matcher cnpj =
                CNPJ_PATTERN.matcher(
                        line
                );

        if (cnpj.find()) {

            return new StopMatch(
                    "CNPJ",
                    0
            );
        }

        Matcher cep =
                CEP_PATTERN.matcher(
                        line
                );

        if (cep.find()) {

            return new StopMatch(
                    "CEP",
                    0
            );
        }

        /*
         * ============================================================
         * 8) CONTATO DEFORMADO POR OCR
         * ============================================================
         */
        Matcher contactContext = CONTACT_CONTEXT_PATTERN.matcher(line);
        if (contactContext.find() && jaTemConteudoIngrediente(collected)) {
            return new StopMatch("CONTACT_TEXT", contactContext.start());
        }

        /*
         * ============================================================
         * 9) TELEFONE / EMAIL / URL
         * ============================================================
         */

        Matcher phone =
                PHONE_PATTERN.matcher(
                        line
                );

        if (phone.find()) {

            return new StopMatch(
                    "PHONE",
                    0
            );
        }

        Matcher email =
                EMAIL_PATTERN.matcher(
                        line
                );

        if (email.find()) {

            return new StopMatch(
                    "EMAIL",
                    0
            );
        }

        Matcher url =
                URL_PATTERN.matcher(
                        line
                );

        if (url.find()) {
            int start = url.start();
            Matcher label = Pattern.compile("(?i)\\b(?:SITE|WEBSITE|ACESSE|VISITE|VISIT)\\s*:?\\s*$")
                    .matcher(line.substring(0, start));
            if (label.find()) start = label.start();
            return new StopMatch("URL", start);
        }

        /*
         * ============================================================
         * 9) ENDEREÇO COM NÚMERO
         * ============================================================
         */

        Matcher street =
                STREET_PATTERN.matcher(
                        line
                );

        if (
                street.find()
                        && (
                        normalized.matches(
                                ".*\\d{1,6}.*"
                        )
                                || CEP_PATTERN
                                .matcher(
                                        normalized
                                )
                                .find()
                )
        ) {

            return new StopMatch(
                    street.group(),
                    street.start()
            );
        }

        return null;
    }

    private static boolean ehStopSempreForte(
            String reason
    ) {

        if (reason == null) {
            return false;
        }

        String n =
                normalizar(
                        reason
                );

        return "CNPJ".equals(n)
                || "CEP".equals(n)
                || "AUTFUNC".equals(n)
                || "AUT FUNC".equals(n)
                || "AUTORIZACAO".equals(n)
                || "REGISTRO ANVISA".equals(n)
                || "NUMERO DE REGISTRO".equals(n)
                || "CODIGO DE BARRAS".equals(n)
                || "GTIN".equals(n)
                || "EAN".equals(n)
                || "LOTE".equals(n)
                || "BATCH".equals(n)
                || "VALIDADE".equals(n)
                || "VENCIMENTO".equals(n)
                || "SAC".equals(n)
                || "ATENDIMENTO AO CONSUMIDOR".equals(n)
                || "WHATSAPP".equals(n)
                || "PHONE".equals(n)
                || "EMAIL".equals(n)
                || "URL".equals(n);
    }

    private static StopMatch encontrarPrimeiroStop(
            String originalLine,
            String[] markers
    ) {

        String normalized =
                normalizar(
                        originalLine
                );

        int bestIndex =
                Integer.MAX_VALUE;

        String bestMarker =
                null;

        for (
                String marker
                : markers
        ) {

            if (
                    marker == null
                            || marker.trim().isEmpty()
            ) {
                continue;
            }

            String normalizedMarker =
                    normalizar(
                            marker
                    );

            int index =
                    indexOfMarkerWithBoundary(
                            normalized,
                            normalizedMarker
                    );

            if (
                    index >= 0
                            && index < bestIndex
            ) {

                bestIndex =
                        index;

                bestMarker =
                        marker;
            }
        }

        if (bestMarker == null) {
            return null;
        }

        return new StopMatch(
                bestMarker,
                bestIndex
        );
    }

    @NonNull
    private static String extrairPrefixoAntesDoStop(
            String line,
            StopMatch stop
    ) {

        if (
                line == null
                        || line.isEmpty()
        ) {
            return EMPTY;
        }

        if (stop == null) {
            return line.trim();
        }

        /*
         * Elementos administrativos que começam a linha.
         */
        if (
                "PHONE".equals(stop.reason)
                        || "EMAIL".equals(stop.reason)
                        || "CNPJ".equals(stop.reason)
                        || "CEP".equals(stop.reason)
        ) {
            return EMPTY;
        }

        if ("CONTACT_TEXT".equals(stop.reason)) {
            int originalIndex = localizarIndiceOriginalPorRegex(
                    line,
                    CONTACT_CONTEXT_PATTERN
            );
            if (originalIndex <= 0) {
                return EMPTY;
            }
            return safeSubstring(line, 0, originalIndex).trim();
        }

        /*
         * Para as barreiras encontradas diretamente na linha original,
         * usamos a posição recebida sem fazer uma segunda conversão.
         * Isso evita o vazamento clássico: \"... Ltda. Av\".
         */
        if (
                "URL".equals(stop.reason)
                        || "SAFETY_OR_MARKETING".equals(stop.reason)
                        || "COMPANY_INLINE".equals(stop.reason)
                        || "CITY_STATE".equals(stop.reason)
                        || "COMPANY_REGISTRATION".equals(stop.reason)
        ) {

            int originalIndex;

            if ("CITY_STATE".equals(stop.reason)) {
                originalIndex =
                        localizarIndiceOriginalPorRegex(
                                line,
                                CITY_STATE_PATTERN
                        );

            } else if ("COMPANY_REGISTRATION".equals(stop.reason)) {
                originalIndex =
                        localizarIndiceOriginalPorRegex(
                                line,
                                COMPANY_REGISTRATION_FRAGMENT_PATTERN
                        );
            } else {
                originalIndex =
                        stop.positionInLine;
            }

            if (originalIndex <= 0) {
                return EMPTY;
            }

            return safeSubstring(
                    line,
                    0,
                    originalIndex
            ).trim();
        }

        /*
         * Origem e endereço devem ser localizados diretamente na linha
         * original, preservando exatamente onde o OCR colocou o marcador.
         */
        if (
                ORIGIN_INLINE_PATTERN
                        .matcher(line)
                        .find()
        ) {
            Matcher m =
                    ORIGIN_INLINE_PATTERN.matcher(
                            line
                    );

            if (m.find() && m.start() > 0) {
                return safeSubstring(
                        line,
                        0,
                        m.start()
                ).trim();
            }
        }

        if (
                ADDRESS_INLINE_PATTERN
                        .matcher(line)
                        .find()
        ) {
            Matcher m =
                    ADDRESS_INLINE_PATTERN.matcher(
                            line
                    );

            if (m.find() && m.start() > 0) {
                return safeSubstring(
                        line,
                        0,
                        m.start()
                ).trim();
            }
        }

        /*
         * Para markers administrativos explícitos, tente primeiro localizar
         * o marcador diretamente na linha original.
         *
         * Isso é importante quando o OCR/normalização altera espaços,
         * pontuação ou acentuação. A conversão por índice normalizado pode
         * parar alguns caracteres antes e deixar um fragmento como
         * "FABRI" dentro do último ingrediente.
         */
        int originalIndex =
                localizarIndiceOriginalPorTextoComFronteira(
                        line,
                        stop.reason
                );

        if (originalIndex < 0) {
            originalIndex =
                    localizarIndiceAproximadoOriginal(
                            line,
                            stop.reason
                    );
        }

        if (originalIndex <= 0) {
            return EMPTY;
        }

        return safeSubstring(
                line,
                0,
                originalIndex
        ).trim();
    }

    /**
     * Encontra o início do primeiro match de um Pattern diretamente na
     * string original. Retorna -1 quando não houver match.
     */
    private static int localizarIndiceOriginalPorRegex(
            String original,
            Pattern pattern
    ) {

        if (
                original == null
                        || original.isEmpty()
                        || pattern == null
        ) {
            return -1;
        }

        Matcher matcher =
                pattern.matcher(
                        original
                );

        return matcher.find()
                ? matcher.start()
                : -1;
    }

    private static int localizarIndiceAproximadoPorNormalizedIndex(
            String original,
            int normalizedIndex
    ) {

        if (
                original == null
                        || normalizedIndex <= 0
        ) {
            return 0;
        }

        StringBuilder normalized =
                new StringBuilder();

        int originalIndex =
                0;

        while (
                originalIndex < original.length()
        ) {

            String piece =
                    normalizar(
                            String.valueOf(
                                    original.charAt(
                                            originalIndex
                                    )
                            )
                    );

            if (!piece.isEmpty()) {

                normalized.append(
                        piece
                );
            }

            if (
                    normalized.length()
                            >= normalizedIndex
            ) {
                break;
            }

            originalIndex++;
        }

        return Math.min(
                originalIndex,
                original.length()
        );
    }

    /**
     * Quando endereço e empresa aparecem colados na mesma linha, tenta
     * localizar o início do pequeno bloco empresarial antes do endereço.
     *
     * Exemplo alvo:
     *   ... PEEL OIL cial Farmacêuticaa Ltda. Av
     *
     * O corte é feito no início do match empresarial, não no \"Ltda\",
     * evitando que o fragmento \"cial Farmacêuticaa\" vire ingrediente.
     */
    private static int localizarInicioEmpresaAntesDaBarreira(
            String line,
            int barrierIndex
    ) {

        if (
                line == null
                        || barrierIndex <= 0
        ) {
            return -1;
        }

        String prefix =
                safeSubstring(
                        line,
                        0,
                        barrierIndex
                );

        Matcher company =
                INLINE_COMPANY_PATTERN.matcher(
                        prefix
                );

        int best =
                -1;

        while (company.find()) {
            if (company.end() <= barrierIndex) {
                best = company.start();
            }
        }

        return best;
    }

    private static boolean jaTemConteudoIngrediente(
            List<String> collected
    ) {

        if (
                collected == null
                        || collected.isEmpty()
        ) {
            return false;
        }

        String text =
                compactarEspacos(
                        String.join(
                                " ",
                                collected
                        )
                );

        return text.length() >= 5;
    }

    /*
     * ================================================================
     * EXTRAÇÃO DE CANDIDATOS
     * ================================================================
     */

    private static List<ScanBackIngredientCandidate> extrairIngredientes(
            IngredientSection section,
            List<ScanOcrToken> spatialTokens
    ) {

        if (
                !section.found
                        || section.rawText.isEmpty()
        ) {

            return Collections.emptyList();
        }

        String raw =
                reconstruirBlocoIngredientesEspacialmente(
                        section,
                        spatialTokens
                );

        if (raw.isEmpty()) {
            raw = section.rawText;
        }

        raw = prepararBlocoIngredientes(raw);

        List<String> parts =
                separarCandidatosIngredientes(
                        raw
                );

        List<ScanBackIngredientCandidate> result =
                new ArrayList<>();

        int position =
                1;

        for (
                String part
                : parts
        ) {

            String cleaned =
                    limparNomeIngrediente(
                            part
                    );

            if (cleaned.isEmpty()) {
                continue;
            }

            result.add(
                    new ScanBackIngredientCandidate(
                            position++,
                            cleaned,
                            normalizarIngrediente(
                                    cleaned
                            ),
                            "UNRESOLVED"
                    )
            );
        }

        return result;
    }

    /**
     * Rows were already ordered geometrically before section detection.
     * A paragraph stays a paragraph. Only repeated, aligned column pairs with
     * recognized INCI evidence may introduce translation parentheses.
     */
    private static String reconstruirBlocoIngredientesEspacialmente(
            IngredientSection section, List<ScanOcrToken> tokens) {
        if (tokens == null || tokens.isEmpty()) return section.rawText;
        java.util.Map<Integer, List<ScanOcrToken>> rows = new java.util.TreeMap<>();
        for (ScanOcrToken t : tokens) {
            if (t.getLineIndex() >= section.startIndex && t.getLineIndex() <= section.endIndex) {
                rows.computeIfAbsent(t.getLineIndex(), k -> new ArrayList<>()).add(t);
            }
        }
        List<String> candidates = new ArrayList<>();
        String pendingLeft = EMPTY, pendingRight = EMPTY;
        Integer columnX = null;
        int lastBottom = -1;
        for (List<ScanOcrToken> row : rows.values()) {
            String text = ScanBackSpatialLayout.join(row);
            HeadingMatch heading = encontrarHeadingIngredientesRobusto(text);
            if (heading != null) {
                // Standalone heading is harmless; inline heading uses paragraph parsing.
                if (limparRestoHeading(substringSafe(text, heading.endInOriginal)).isEmpty()) continue;
                return section.rawText;
            }
            StopMatch stop = detectarFimIngredientes(text, candidates);
            if (stop != null) {
                if (extrairPrefixoAntesDoStop(text, stop).isEmpty()) break;
                return section.rawText;
            }
            // Punctuation is direct evidence of a running ingredient list.
            if (text.matches(".*[,;()|].*") || row.size() < 2) return section.rawText;
            int split = -1, gap = 0;
            List<Integer> heights = new ArrayList<>();
            for (ScanOcrToken t : row) heights.add(t.getHeight());
            Collections.sort(heights);
            int height = heights.get(heights.size() / 2);
            for (int i = 1; i < row.size(); i++) {
                int distance = row.get(i).getBoundingBox().left - row.get(i - 1).getBoundingBox().right;
                if (distance > gap) { split = i; gap = distance; }
            }
            if (split < 1 || gap < height * 2) return section.rawText;
            int x = row.get(split).getBoundingBox().left;
            if (columnX != null && Math.abs(x - columnX) > height * 3) return section.rawText;
            columnX = columnX == null ? x : columnX;
            String left = ScanBackSpatialLayout.join(row.subList(0, split));
            String right = ScanBackSpatialLayout.join(row.subList(split, row.size()));
            // A known ingredient on the right is not evidence of a translation.
            if (linhaComecaComInciConhecido(right)) return section.rawText;
            if (!pendingLeft.isEmpty()) {
                if (row.get(0).getBoundingBox().top - lastBottom > height * 2) return section.rawText;
                left = pendingLeft + " " + left;
                right = pendingRight + " " + right;
            }
            if (inciCompleto(left)) {
                candidates.add(left + " (" + right + ")");
                pendingLeft = EMPTY;
                pendingRight = EMPTY;
            } else if (prefixoInci(left)) {
                pendingLeft = left;
                pendingRight = right;
            } else {
                return section.rawText;
            }
            lastBottom = row.get(0).getBoundingBox().bottom;
        }
        if (candidates.size() < 2 || !pendingLeft.isEmpty()) return section.rawText;
        Log.d(TAG, "BACK_LAYOUT_COLUMNS=CONFIRMED pairs=" + candidates.size());
        return String.join(", ", candidates);
    }

    private static boolean inciCompleto(String value) {
        String n = normalizar(value);
        for (String known : COMMON_INCI_BOUNDARIES) {
            if (normalizar(known).equals(n)) return true;
        }
        return false;
    }

    private static boolean prefixoInci(String value) {
        String n = normalizar(value);
        if (n.isEmpty()) return false;
        for (String known : COMMON_INCI_BOUNDARIES) {
            if (normalizar(known).startsWith(n + " ")) return true;
        }
        return false;
    }

    private static boolean linhaComecaComInciConhecido(String value) {
        String n = normalizar(value);
        for (String known : inciOrdenadosPorComprimento()) {
            String k = normalizar(known);
            if (n.equals(k) || (n.startsWith(k) && n.length() > k.length()
                    && " ,;.:()".indexOf(n.charAt(k.length())) >= 0)) return true;
        }
        return false;
    }

    private static List<String> inciOrdenadosPorComprimento() {
        List<String> values =
                new ArrayList<>(COMMON_INCI_BOUNDARIES);

        values.sort(
                (a, b) -> Integer.compare(
                        normalizar(b).length(),
                        normalizar(a).length()
                )
        );

        return values;
    }

    private static String prepararBlocoIngredientes(
            String value
    ) {

        String s =
                value == null
                        ? EMPTY
                        : value;

        s =
                normalizarOCRBasico(
                        s
                );

        /*
         * Letras isoladas usadas pelo OCR como separadores.
         *
         * Exemplos:
         *
         * DIMETHICONOL E ALPHA...
         * DIMETHICONOL I ALPHA...
         * DIMETHICONOL L ALPHA...
         *
         * Só aplicamos quando existe outro token imediatamente depois.
         */
        s =
                s.replaceAll(
                        "(?i)\\s+[EIL]\\s+(?=[A-Z0-9])",
                        ", "
                );

        /*
         * Ponto como separador.
         *
         * Exemplos:
         *
         * LINALOOL. LIMONENE
         * LINALOOL .FABRICADO
         */
        s =
                s.replaceAll(
                        "(?i)(?<=[A-Z0-9\\)])"
                                + "\\.(?=\\s*[A-Z][A-Z0-9/-]{2,})",
                        ", "
                );

        s =
                compactarEspacos(
                        s
                );

        return limparPontosFinais(
                s
        );
    }

    private static List<String> separarCandidatosIngredientes(
            String raw
    ) {

        List<String> result =
                new ArrayList<>();

        if (
                raw == null
                        || raw.trim().isEmpty()
        ) {
            return result;
        }

        String prepared =
                compactarEspacos(
                        raw
                );

        /*
         * Primeiro:
         * separadores reais.
         */
        String[] explicit =
                prepared.split(
                        "\\s*[,;|•]\\s*"
                );

        /*
         * Caso o OCR tenha perdido as vírgulas e criado grandes espaços.
         */
        if (explicit.length <= 1) {

            explicit =
                    prepared.split(
                            "\\s{2,}"
                    );
        }

        for (
                String chunk
                : explicit
        ) {

            String clean =
                    limparSeparadorResidual(
                            chunk
                    );

            if (clean.isEmpty()) {
                continue;
            }

            List<String> segmented =
                    segmentarPorINCIConhecido(
                            clean
                    );

            if (
                    segmented == null
                            || segmented.isEmpty()
            ) {

                result.add(
                        clean
                );

                continue;
            }

            result.addAll(
                    segmented
            );
        }

        result =
                consolidarFragmentos(
                        result
                );

        return deduplicarPreservandoOrdem(
                result
        );
    }

    private static List<String> segmentarPorINCIConhecido(
            String chunk
    ) {

        String working =
                compactarEspacos(
                        chunk
                );

        if (working.isEmpty()) {
            return Collections.emptyList();
        }

        String upper =
                working.toUpperCase(
                        Locale.ROOT
                );

        List<Boundary> found =
                new ArrayList<>();

        /*
         * A partir da V7.0, um INCI conhecido representa o INÍCIO
         * de um candidato, e não o candidato inteiro.
         *
         * Isso é importante porque o OCR frequentemente devolve:
         *
         *     PROPANE (PROPANO)
         *     GLYCERIN GUCEROL
         *     GLYCERYL STEARATE (MONOESTEARATO ...)
         *
         * O texto após o INCI continua pertencendo ao mesmo candidato
         * até encontrarmos o próximo INCI conhecido.
         */
        for (
                String known
                : inciOrdenadosPorComprimento()
        ) {

            String k =
                    known.toUpperCase(
                            Locale.ROOT
                    );

            int search =
                    0;

            while (
                    search < upper.length()
            ) {

                int idx =
                        upper.indexOf(
                                k,
                                search
                        );

                if (idx < 0) {
                    break;
                }

                int end =
                        idx + k.length();

                boolean left =
                        idx == 0
                                || !Character.isLetterOrDigit(
                                upper.charAt(
                                        idx - 1
                                )
                        );

                boolean right =
                        end >= upper.length()
                                || !Character.isLetterOrDigit(
                                upper.charAt(
                                        end
                                )
                        );

                boolean insideParentheses =
                        estaDentroDeParenteses(
                                upper,
                                idx
                        );

                boolean nestedTranslatedIngredient =
                        insideParentheses
                                && pareceNovoIngredienteComTraducaoAninhada(
                                upper,
                                idx,
                                end
                        );

                if (
                        left
                                && right
                                && (
                                !insideParentheses
                                        || nestedTranslatedIngredient
                        )
                ) {

                    found.add(
                            new Boundary(
                                    idx,
                                    end,
                                    known
                            )
                    );
                }

                search =
                        Math.max(
                                end,
                                search + 1
                        );
            }
        }

        if (found.isEmpty()) {
            return Collections.singletonList(
                    working
            );
        }

        found.sort(
                Comparator.comparingInt(
                        a -> a.start
                )
        );

        /*
         * Remove boundaries duplicadas ou sobrepostas.
         */
        List<Boundary> unique =
                new ArrayList<>();

        int lastStart = -1;
        int lastEnd = -1;

        for (
                Boundary b
                : found
        ) {

            if (
                    b.start == lastStart
                            && b.end <= lastEnd
            ) {
                continue;
            }

            if (
                    b.start < lastEnd
            ) {
                continue;
            }

            unique.add(
                    b
            );

            lastStart =
                    b.start;

            lastEnd =
                    b.end;
        }

        if (unique.isEmpty()) {
            return Collections.singletonList(
                    working
            );
        }

        /*
         * Se o primeiro INCI conhecido não começa o chunk, não temos
         * evidência estrutural suficiente para separar o prefixo.
         *
         * Em vez de correr o risco de transformar a tradução OCR de
         * um ingrediente anterior em um novo ingrediente, preservamos
         * o chunk inteiro. O matcher posterior continua responsável
         * pela resolução semântica.
         */
        if (unique.get(0).start > 0) {
            return Collections.singletonList(
                    working
            );
        }

        List<String> parts =
                new ArrayList<>();

        for (
                int i = 0;
                i < unique.size();
                i++
        ) {

            Boundary current =
                    unique.get(
                            i
                    );

            int segmentEnd =
                    i + 1 < unique.size()
                            ? unique.get(i + 1).start
                            : working.length();

            if (
                    segmentEnd <= current.start
            ) {
                continue;
            }

            String segment =
                    working.substring(
                            current.start,
                            segmentEnd
                    ).trim();

            if (!segment.isEmpty()) {
                parts.add(
                        segment
                );
            }
        }

        if (parts.isEmpty()) {
            return Collections.singletonList(
                    working
            );
        }

        return consolidarFragmentos(
                parts
        );
    }

    private static boolean pareceNovoIngredienteComTraducaoAninhada(
            String text,
            int ingredientStart,
            int ingredientEnd
    ) {
        if (text == null || ingredientStart < 0 || ingredientEnd <= ingredientStart || ingredientEnd >= text.length()) {
            return false;
        }
        int closeOuter = text.indexOf(')', ingredientEnd);
        int openNext = text.indexOf('(', ingredientEnd);
        return openNext >= 0 && (closeOuter < 0 || openNext < closeOuter);
    }

    private static boolean estaDentroDeParenteses(
            String text,
            int index
    ) {
        if (text == null || index <= 0) {
            return false;
        }
        int depth = 0;
        for (int i = 0; i < index && i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && depth > 0) depth--;
        }
        return depth > 0;
    }

    private static List<String> consolidarFragmentos(
            List<String> input
    ) {

        List<String> out =
                new ArrayList<>();

        if (input == null) {
            return out;
        }

        for (
                String item
                : input
        ) {

            String s =
                    limparSeparadorResidual(
                            item
                    );

            if (s.isEmpty()) {
                continue;
            }

            String n =
                    normalizar(
                            s
                    );

            /*
             * Letras isoladas são ruído frequente do OCR.
             */
            if (n.length() == 1) {
                continue;
            }

            /*
             * CI pode ser prefixo de Color Index.
             */
            if ("CI".equals(n)) {

                out.add(
                        s
                );

                continue;
            }

            /*
             * Números isolados só são preservados quando podem
             * completar um CI já existente.
             */
            if (
                    n.matches(
                            "^[0-9 .:/-]+$"
                    )
            ) {

                if (!out.isEmpty()) {

                    int last =
                            out.size() - 1;

                    String previous =
                            normalizar(
                                    out.get(
                                            last
                                    )
                            );

                    if (
                            "CI".equals(
                                    previous
                            )
                    ) {

                        out.set(
                                last,
                                compactarEspacos(
                                        out.get(
                                                last
                                        )
                                                + " "
                                                + s
                                )
                        );
                    }
                }

                continue;
            }

            out.add(
                    s
            );
        }

        return out;
    }

    private static boolean temPontuacaoForte(
            String s
    ) {

        return s != null
                && (
                s.contains(",")
                        || s.contains(";")
        );
    }

    private static boolean ehIngredienteMuitoCurto(
            String s
    ) {

        String n =
                normalizar(
                        s
                );

        return "AQUA".equals(n)
                || "G".equals(n)
                || "L".equals(n);
    }

    private static String limparNomeIngrediente(
            String value
    ) {

        String s =
                normalizarOCRBasico(
                        value
                )
                        .replaceFirst(
                                "^\\d+[.)]\\s*",
                                EMPTY
                        )
                        .replaceFirst(
                                "^(?:INGREDIENTES?|INGREDIENTS?|INGREDIENTI|COMPOSICAO|COMPOSITION|COMPOSICION|INCI)"
                                        + "\\s*[:;.-]?\\s*",
                                EMPTY
                        )
                        .replaceFirst(
                                "^(?:[IL]\\s+)"
                                        + "(?=(?:LIMONENE|LINALOOL)\\b)",
                                EMPTY
                        )
                        .replaceAll(
                                "^[,;|.\\- ]+",
                                EMPTY
                        )
                        .replaceAll(
                                "[,;|. ]+$",
                                EMPTY
                        )
                        .replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();

        s = s.replaceFirst(
                "^(?:INGREDIENTS?|INGREDIENTES?)\\s+",
                EMPTY
        );

        s = s.replaceFirst(
                "^[:;,.\\-]+\\s*",
                EMPTY
        );

        s = s.replaceAll(
                "\\s*[:;]\\s*",
                " "
        );

        s = s.replaceAll(
                "\\s+",
                " "
        ).trim();

        if (
                s.isEmpty()
                        || s.length() < 2
        ) {
            return EMPTY;
        }

        if (
                ehTextoAdministrativoObvio(
                        s
                )
        ) {
            return EMPTY;
        }

        if (
                ADDRESS_INLINE_PATTERN.matcher(s).find()
                        || INLINE_COMPANY_PATTERN.matcher(s).find()
        ) {
            return EMPTY;
        }

        if (
                s.matches(
                        "^[0-9 .:/\\-]+$"
                )
        ) {
            return EMPTY;
        }

        /*
         * Proteção adicional contra telefone/e-mail/site.
         */
        if (
                PHONE_PATTERN
                        .matcher(
                                s
                        )
                        .find()
                        || EMAIL_PATTERN
                        .matcher(
                                s
                        )
                        .find()
                        || URL_PATTERN
                        .matcher(s)
                        .find()
                        || CONTACT_CONTEXT_PATTERN.matcher(s).find()
        ) {
            return EMPTY;
        }

        return s;
    }

    private static String limparSeparadorResidual(
            String value
    ) {

        if (value == null) {
            return EMPTY;
        }

        return compactarEspacos(
                value
        )
                .replaceAll(
                        "^[,;|. ]+",
                        EMPTY
                )
                .replaceAll(
                        "[,;|. ]+$",
                        EMPTY
                )
                .trim();
    }

    private static String normalizarIngrediente(
            String value
    ) {

        return normalizarOCRBasico(
                value
        )
                .replaceAll(
                        "\\s*/\\s*",
                        "/"
                )
                .replaceAll(
                        "\\s*-\\s*",
                        "-"
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    /*
     * ================================================================
     * FABRICANTE / IMPORTADOR / ENDEREÇO
     * ================================================================
     */

    private static String extrairFabricante(
            List<String> lines
    ) {

        for (
                int i = 0;
                i < lines.size();
                i++
        ) {

            String line =
                    lines.get(i);

            CompanyMarker marker =
                    encontrarMarcadorEmpresa(
                            line
                    );

            if (marker == null) {
                continue;
            }

            String remainder =
                    escolherEmpresaDepoisDoMarcador(
                            marker.remainder,
                            lines,
                            i
                    );

            if (!remainder.isEmpty()) {

                return remainder;
            }

            /*
             * Só aceita texto anterior ao marcador quando
             * já parece empresa.
             */
            if (
                    !marker.prefix.isEmpty()
                            && ehEmpresaClara(
                            marker.prefix
                    )
            ) {

                return limparEmpresa(
                        marker.prefix
                );
            }

            /*
             * Nome da empresa pode estar na linha seguinte.
             */
            for (
                    int j = i + 1;
                    j <= Math.min(
                            lines.size() - 1,
                            i + 2
                    );
                    j++
            ) {

                String candidate =
                        limparEmpresa(
                                lines.get(j)
                        );

                if (
                        ehEmpresaClara(
                                candidate
                        )
                                && !pareceImportacao(
                                lines,
                                j
                        )
                ) {

                    return candidate;
                }
            }
        }

        /*
         * Caso de OCR em que o nome da empresa aparece colado ao final
         * da composição e não existe um marcador explícito como
         * "FABRICADO POR":
         *
         *   ... PEEL OIL cial Farmacêutica Ltda. Av. Rui Barbosa
         *
         * A expressão empresarial já usada como barreira do extractor
         * permite recuperar somente o bloco da empresa.
         */
        for (String line : lines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }

            Matcher inlineCompany =
                    INLINE_COMPANY_PATTERN.matcher(line);

            if (inlineCompany.find()) {
                String candidate =
                        limparEmpresa(inlineCompany.group());

                if (ehEmpresaClara(candidate)) {
                    return candidate;
                }
            }
        }

        /*
         * Fallback conservador:
         * empresa conhecida + sufixo empresarial.
         */
        for (
                int i = 0;
                i < lines.size();
                i++
        ) {

            String line =
                    compactarEspacos(
                            normalizarOCRBasico(
                                    lines.get(i)
                            )
                    );

            if (line.isEmpty()) {
                continue;
            }

            if (
                    pareceImportacao(
                            lines,
                            i
                    )
                            || pareceDistribuicao(
                            lines,
                            i
                    )
            ) {
                continue;
            }

            if (
                    temContextoAdministrativo(
                            line
                    )
            ) {
                continue;
            }

            if (
                    contemMarcadorCorporativo(
                            line
                    )
                            && temSufixoEmpresarial(
                            line
                    )
            ) {

                String candidate =
                        limparEmpresa(
                                line
                        );

                if (
                        ehEmpresaClara(
                                candidate
                        )
                ) {

                    return candidate;
                }
            }
        }

        return EMPTY;
    }

    private static String escolherEmpresaDepoisDoMarcador(
            String remainder,
            List<String> lines,
            int index
    ) {

        if (remainder == null) {
            remainder =
                    EMPTY;
        }

        String candidate =
                limparEmpresa(
                        remainder
                );

        if (
                ehEmpresaClara(
                        candidate
                )
        ) {

            return cortarTrechoAdministrativo(
                    candidate
            );
        }

        if (
                index + 1
                        < lines.size()
        ) {

            String next =
                    limparEmpresa(
                            lines.get(
                                    index + 1
                            )
                    );

            if (
                    ehEmpresaClara(
                            next
                    )
                            && !pareceImportacao(
                            lines,
                            index + 1
                    )
            ) {

                return cortarTrechoAdministrativo(
                        next
                );
            }
        }

        return EMPTY;
    }

    private static CompanyMarker encontrarMarcadorEmpresa(
            String line
    ) {

        if (
                line == null
                        || line.trim().isEmpty()
        ) {
            return null;
        }

        String normalized =
                normalizar(
                        line
                );

        int best =
                Integer.MAX_VALUE;

        String bestMarker =
                null;

        String bestMarkerNormalized =
                null;

        for (
                String marker
                : MANUFACTURER_MARKERS
        ) {

            String m =
                    normalizar(
                            marker
                    );

            int idx =
                    indexOfMarkerWithBoundary(
                            normalized,
                            m
                    );

            if (
                    idx >= 0
                            && idx < best
            ) {

                best =
                        idx;

                bestMarker =
                        marker;

                bestMarkerNormalized =
                        m;
            }
        }

        if (bestMarker == null) {
            return null;
        }

        Matcher originalMarker = Pattern.compile(
                "(?i)(?<![A-Z0-9])"
                        + Pattern.quote(bestMarker.trim())
                        + "(?![A-Z0-9])"
        ).matcher(line);

        if (originalMarker.find()) {
            String prefix = safeSubstring(line, 0, originalMarker.start()).trim();
            String remainder = safeSubstring(line, originalMarker.end(), line.length()).trim();
            return new CompanyMarker(prefix, remainder, bestMarker, bestMarkerNormalized);
        }

        int originalIndex = localizarIndiceAproximadoOriginal(line, bestMarker);
        if (originalIndex < 0) originalIndex = best;

        String prefix = safeSubstring(line, 0, originalIndex).trim();
        String remainder = safeSubstring(line, originalIndex + bestMarker.length(), line.length()).trim();

        return new CompanyMarker(prefix, remainder, bestMarker, bestMarkerNormalized);
    }

    /**
     * Localiza um termo INCI na linha original respeitando fronteiras de
     * palavra. Isso é preferível ao mapeamento por índice normalizado para
     * evitar perder o primeiro caractere do ingrediente, como em:
     *   \"UENTES: BUTANE...\" -> \"BUTANE...\"
     */
    private static int localizarIndiceOriginalPorTextoComFronteira(
            String original,
            String marker
    ) {

        if (
                original == null
                        || marker == null
                        || marker.trim().isEmpty()
        ) {
            return -1;
        }

        String normalizedMarker =
                normalizar(
                        marker
                );

        if (normalizedMarker.isEmpty()) {
            return -1;
        }

        /*
         * 1) Primeiro tenta no texto ORIGINAL.
         * Isso preserva a posição exata e evita cortes tardios como
         * "LINALOOL .FABRI".
         */
        Pattern originalPattern =
                Pattern.compile(
                        "(?i)(?<![A-Z0-9])"
                                + Pattern.quote(marker.trim())
                                + "(?![A-Z0-9])"
                );

        Matcher originalMatcher =
                originalPattern.matcher(original);

        if (originalMatcher.find()) {
            return originalMatcher.start();
        }

        /*
         * 2) Fallback para OCR que perdeu acentos ou alterou espaçamento.
         */
        Pattern normalizedPattern =
                Pattern.compile(
                        "(?i)(?<![A-Z0-9])"
                                + Pattern.quote(normalizedMarker)
                                + "(?![A-Z0-9])"
                );

        Matcher normalizedMatcher =
                normalizedPattern.matcher(
                        normalizar(original)
                );

        if (!normalizedMatcher.find()) {
            return -1;
        }

        return localizarIndiceAproximadoPorNormalizedIndex(
                original,
                normalizedMatcher.start()
        );
    }

    private static int localizarIndiceAproximadoOriginal(
            String original,
            String marker
    ) {

        String nOriginal =
                normalizar(
                        original
                );

        String nMarker =
                normalizar(
                        marker
                );

        int normalizedIndex =
                nOriginal.indexOf(
                        nMarker
                );

        if (normalizedIndex < 0) {
            return -1;
        }

        int originalPos =
                0;

        int normalizedPos =
                0;

        while (
                originalPos
                        < original.length()
                        && normalizedPos
                        < normalizedIndex
        ) {

            char c =
                    original.charAt(
                            originalPos++
                    );

            String one =
                    normalizar(
                            String.valueOf(
                                    c
                            )
                    );

            if (!one.isEmpty()) {
                normalizedPos +=
                        one.length();
            }
        }

        return Math.min(
                originalPos,
                original.length()
        );
    }

    private static boolean ehEmpresaClara(
            String value
    ) {

        if (value == null) {
            return false;
        }

        String s =
                compactarEspacos(
                        normalizarOCRBasico(
                                value
                        )
                );

        if (
                s.length() < 5
                        || s.length() > 150
        ) {
            return false;
        }

        /*
         * Sufixos empresariais fazem parte da própria identidade da
         * empresa e, portanto, não podem ser tratados como "lixo
         * administrativo" durante a validação do candidato.
         */
        String adminCheck =
                s;

        for (String suffix : COMPANY_SUFFIXES) {
            adminCheck =
                    adminCheck.replaceAll(
                            "(?i)\\b"
                                    + Pattern.quote(
                                    normalizar(suffix)
                            )
                                    + "\\b",
                            " "
                    );
        }

        if (
                temRuidoAdministrativo(
                        adminCheck
                )
        ) {
            return false;
        }

        if (
                CNPJ_PATTERN
                        .matcher(
                                s
                        )
                        .find()
                        || CEP_PATTERN
                        .matcher(
                                s
                        )
                        .find()
        ) {
            return false;
        }

        if (
                SAC_PHONE_PATTERN
                        .matcher(
                                s
                        )
                        .find()
        ) {
            return false;
        }

        if (
                URL_PATTERN
                        .matcher(
                                s
                        )
                        .find()
        ) {
            return false;
        }

        boolean corporate =
                temSufixoEmpresarial(
                        s
                )
                        || contemMarcadorCorporativo(
                        s
                );

        if (!corporate) {
            return false;
        }

        int letters =
                contarLetras(
                        s
                );

        return letters >= 6;
    }

    private static String limparEmpresa(
            String value
    ) {

        if (value == null) {
            return EMPTY;
        }

        String s =
                compactarEspacos(
                        normalizarOCRBasico(
                                value
                        )
                );

        if (s.isEmpty()) {
            return EMPTY;
        }

        s =
                cortarTrechoAdministrativo(
                        s
                );

        s =
                s.replaceFirst(
                        "^[,:;.|\\- ]+",
                        EMPTY
                );

        return compactarEspacos(
                s
        );
    }

    private static String cortarTrechoAdministrativo(
            String s
    ) {

        String upper =
                normalizar(
                        s
                );

        int cut =
                Integer.MAX_VALUE;

        String[] stops = {
                " CNPJ",
                " CEP",
                " SAC",
                " AUTFUNC",
                " AUT FUNC",
                " REGISTRO",
                " LOTE",
                " CONTEUDO",
                " IMPORTADO POR",
                " IMPORTADO",
                " DISTRIBUIDO POR",
                " DISTRIBUIDORA",
                " INDUSTRIA BRASILEIRA",
                " PAIS DE ORIGEM",
                " WWW.",
                " HTTP",
                " AVENIDA",
                " RUA",
                " CALLE",
                " CARRERA",
                " PISO"
        };

        for (
                String stop
                : stops
        ) {

            int idx =
                    upper.indexOf(
                            normalizar(
                                    stop
                            )
                    );

            if (
                    idx > 0
                            && idx < cut
            ) {

                cut =
                        idx;
            }
        }

        if (
                cut
                        != Integer.MAX_VALUE
        ) {

            s =
                    s.substring(
                            0,
                            Math.min(
                                    cut,
                                    s.length()
                            )
                    ).trim();
        }

        return s;
    }

    private static String extrairEndereco(
            List<String> lines,
            String manufacturer
    ) {

        Set<String> candidates =
                new LinkedHashSet<>();

        for (
                int i = 0;
                i < lines.size();
                i++
        ) {

            String line =
                    compactarEspacos(
                            normalizarOCRBasico(
                                    lines.get(i)
                            )
                    );

            if (line.isEmpty()) {
                continue;
            }

            if (
                    CNPJ_PATTERN
                            .matcher(
                                    line
                            )
                            .find()
                            && !temTextoDeEndereco(
                            line
                    )
            ) {
                continue;
            }

            if (
                    STREET_PATTERN
                            .matcher(
                                    line
                            )
                            .find()
                            || pareceEnderecoInternacional(
                            line
                    )
            ) {

                if (
                        !temRuidoAdministrativoExcessivo(
                                line
                        )
                ) {

                    candidates.add(
                            limparEndereco(
                                    line
                            )
                    );
                }
            }
        }

        if (!candidates.isEmpty()) {

            for (
                    String candidate
                    : candidates
            ) {

                if (
                        CEP_PATTERN
                                .matcher(
                                        candidate
                                )
                                .find()
                                || STREET_PATTERN
                                .matcher(
                                        candidate
                                )
                                .find()
                ) {

                    return candidate;
                }
            }

            return candidates.iterator().next();
        }

        return EMPTY;
    }

    private static boolean temTextoDeEndereco(
            String line
    ) {

        return STREET_PATTERN
                .matcher(
                        line
                )
                .find()
                || line.matches(
                ".*\\b\\d{1,6}\\b.*"
        )
                || line.contains(
                "PISO"
        )
                || line.contains(
                "VITACURA"
        )
                || line.contains(
                "CEP"
        );
    }

    private static boolean pareceEnderecoInternacional(
            String line
    ) {

        if (line == null) {
            return false;
        }

        String n =
                normalizar(
                        line
                );

        return (
                n.contains(
                        "VITACURA"
                )
                        || n.contains(
                        "MUNRO"
                )
                        || n.contains(
                        "TEPALCAPA"
                )
                        || n.contains(
                        "TULTITLAN"
                )
                        || n.contains(
                        "ESTADO DE MEXICO"
                )
        )
                && line.matches(
                ".*\\d{1,6}.*"
        );
    }

    private static String limparEndereco(
            String line
    ) {

        String s =
                compactarEspacos(
                        normalizarOCRBasico(
                                line
                        )
                );

        s =
                CNPJ_PATTERN
                        .matcher(
                                s
                        )
                        .replaceAll(
                                EMPTY
                        )
                        .trim();

        /*
         * Quando empresa + endereço aparecem na mesma linha, não podemos
         * guardar o prefixo da composição como se fosse parte do endereço.
         * O endereço começa no primeiro marcador de via.
         */
        Matcher streetStart =
                STREET_PATTERN.matcher(s);

        if (streetStart.find() && streetStart.start() > 0) {
            s = s.substring(streetStart.start()).trim();
        }

        s =
                cortarTrechoAdministrativoEndereco(
                        s
                );

        return compactarEspacos(
                s
        );
    }

    private static String cortarTrechoAdministrativoEndereco(
            String s
    ) {

        String upper =
                normalizar(
                        s
                );

        int cut =
                Integer.MAX_VALUE;

        String[] stops = {
                " CNPJ",
                " SAC",
                " AUTFUNC",
                " AUT FUNC",
                " IMPORTADO POR",
                " DISTRIBUIDO POR"
        };

        for (
                String stop
                : stops
        ) {

            int idx =
                    upper.indexOf(
                            normalizar(
                                    stop
                            )
                    );

            if (
                    idx > 0
                            && idx < cut
            ) {

                cut =
                        idx;
            }
        }

        if (
                cut
                        != Integer.MAX_VALUE
        ) {

            s =
                    s.substring(
                            0,
                            Math.min(
                                    cut,
                                    s.length()
                            )
                    ).trim();
        }

        return s;
    }

    /*
     * ================================================================
     * CONTATO
     * ================================================================
     */

    private static String extrairContato(
            List<String> lines
    ) {

        LinkedHashSet<String> contacts =
                new LinkedHashSet<>();

        for (
                String line
                : lines
        ) {

            String original =
                    line == null
                            ? EMPTY
                            : line;

            Matcher sac =
                    SAC_PHONE_PATTERN.matcher(
                            original
                    );

            while (sac.find()) {

                contacts.add(
                        normalizarTelefone(
                                sac.group()
                        )
                );
            }

            Matcher phone =
                    PHONE_PATTERN.matcher(
                            original
                    );

            while (phone.find()) {

                String number =
                        normalizarTelefone(
                                phone.group()
                        );

                if (
                        !number.isEmpty()
                                && temEvidenciaDeTelefone(original, phone.group())
                                && !pareceNumeroAdministrativo(
                                number
                        )
                ) {

                    contacts.add(
                            number
                    );
                }
            }

            Matcher email =
                    EMAIL_PATTERN.matcher(
                            original
                    );

            while (email.find()) {

                contacts.add(
                        email.group().trim()
                );
            }

            Matcher contactContext =
                    CONTACT_CONTEXT_PATTERN.matcher(original);
            while (contactContext.find()) {
                String candidate = compactarEspacos(contactContext.group());
                if (!candidate.isEmpty()) contacts.add(candidate);
            }

            Matcher residual = CONTACT_RESIDUAL_PATTERN.matcher(original);
            while (residual.find()) {
                String candidate = compactarEspacos(residual.group(1));
                if (pareceContatoResidual(candidate)) {
                    contacts.add(candidate);
                }
            }
        }

        return String.join(
                " | ",
                contacts
        );
    }

    private static boolean temEvidenciaDeTelefone(String line, String number) {
        return Pattern.compile("\\b(?:SAC|TEL|TELEFONE|TELEPHONE|PHONE|FONE|CALL|CONTATO)\\b")
                .matcher(normalizar(line)).find()
                || number.trim().startsWith("+")
                || number.contains("(")
                || number.matches(".*\\d{4,5}-\\d{4}.*");
    }

    private static boolean pareceContatoResidual(String value) {
        if (value == null || value.trim().length() < 3) {
            return false;
        }

        String normalized = normalizar(value);
        return normalized.contains("@")
                || normalized.contains("CALL")
                || normalized.contains("PHONE")
                || normalized.contains("FONE")
                || normalized.contains("SUPPORT")
                || normalized.contains("SOPPORT")
                || normalized.contains("SUPPOR")
                || PHONE_PATTERN.matcher(value).find()
                || EMAIL_PATTERN.matcher(value).find();
    }

    private static boolean pareceNumeroAdministrativo(
            String value
    ) {

        String digits =
                value.replaceAll(
                        "\\D",
                        EMPTY
                );

        if (
                digits.length() >= 10
                        && digits.length() <= 14
        ) {

            return digits.matches(
                    "0{6,}"
            )
                    || digits.startsWith(
                    "01.615"
            )
                    || digits.startsWith(
                    "5511"
            );
        }

        return false;
    }

    private static String normalizarTelefone(
            String value
    ) {

        return compactarEspacos(
                value
        )
                .replaceAll(
                        "[()]",
                        EMPTY
                )
                .trim();
    }

    /*
     * ================================================================
     * PAÍS
     * ================================================================
     */

    private static String extrairPais(
            List<String> lines,
            String manufacturer
    ) {

        /*
         * 1) Evidência explícita de origem.
         */
        for (
                String line
                : lines
        ) {

            String n =
                    normalizar(
                            line
                    );

            for (
                    String marker
                    : BRAZIL_ORIGIN_MARKERS
            ) {

                if (
                        contémTermoComFronteira(
                                n,
                                marker
                        )
                                || n.contains(
                                normalizar(
                                        marker
                                )
                        )
                ) {

                    return "BRASIL";
                }

                if (
                        normalizar(
                                marker
                        )
                                .contains(
                                        "INDUSTRIA"
                                )
                                && (
                                n.contains(
                                        "NDUSTRIA BRASILEIRA"
                                )
                                        || n.contains(
                                        "INDUSTRA BRASILEIRA"
                                )
                                        || n.contains(
                                        "INDUSTRIA 8RASILENA"
                                )
                                        || n.contains(
                                        "INDUSTRA 8RASILENA"
                                )
                        )
                ) {

                    return "BRASIL";
                }
            }

            String explicit =
                    extrairPaisDeExpressaoExplicita(
                            n
                    );

            if (
                    !explicit.isEmpty()
            ) {

                return explicit;
            }
        }

        /*
         * 2) Fabricante explicitamente brasileiro.
         */
        if (
                manufacturer != null
                        && !manufacturer.isEmpty()
        ) {

            String m =
                    normalizar(
                            manufacturer
                    );

            if (
                    m.contains(
                            "BRASIL"
                    )
                            || m.contains(
                            "BRAZIL"
                    )
            ) {

                return "BRASIL";
            }
        }

        /*
         * 3) País em linha isolada.
         */
        for (
                String line
                : lines
        ) {

            String n =
                    normalizar(
                            line
                    );

            if (
                    n.isEmpty()
                            || n.length() > 60
            ) {
                continue;
            }

            String country =
                    matchCountryWholeLine(
                            n
                    );

            if (
                    !country.isEmpty()
            ) {

                return country;
            }
        }

        return EMPTY;
    }

    private static String extrairPaisDeExpressaoExplicita(
            String line
    ) {

        if (
                line.contains(
                        "MADE IN "
                )
        ) {

            String tail =
                    line.substring(
                            line.indexOf(
                                    "MADE IN "
                            )
                                    + 8
                    ).trim();

            return normalizarNomePais(
                    tail
            );
        }

        if (
                line.contains(
                        "FABRICADO NO "
                )
        ) {

            String tail =
                    line.substring(
                            line.indexOf(
                                    "FABRICADO NO "
                            )
                                    + 13
                    ).trim();

            return normalizarNomePais(
                    tail
            );
        }

        if (
                line.contains(
                        "PAIS DE ORIGEM"
                )
        ) {

            String tail =
                    line.replaceFirst(
                            ".*PAIS DE ORIGEM\\s*[:.-]?\\s*",
                            ""
                    );

            return normalizarNomePais(
                    tail
            );
        }

        return EMPTY;
    }

    private static String matchCountryWholeLine(
            String line
    ) {

        for (
                String country
                : COUNTRY_NAMES
        ) {

            String n =
                    normalizar(
                            country
                    );

            if (
                    line.equals(
                            n
                    )
            ) {

                return normalizarNomePais(
                        n
                );
            }
        }

        return EMPTY;
    }

    private static String normalizarNomePais(
            String value
    ) {

        String n =
                normalizar(
                        value
                )
                        .replaceAll(
                                "[^A-Z ]",
                                " "
                        )
                        .replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();

        switch (n) {

            case "BRASIL":
            case "BRAZIL":
                return "BRASIL";

            case "ARGENTINA":
                return "ARGENTINA";

            case "CHILE":
                return "CHILE";

            case "COLOMBIA":
                return "COLOMBIA";

            case "MEXICO":
                return "MEXICO";

            case "PERU":
                return "PERU";

            case "URUGUAI":
            case "URUGUAY":
                return "URUGUAI";

            case "PARAGUAI":
            case "PARAGUAY":
                return "PARAGUAI";

            case "BOLIVIA":
                return "BOLIVIA";

            case "CANADA":
                return "CANADA";

            case "PORTUGAL":
                return "PORTUGAL";

            case "FRANCA":
            case "FRANCE":
                return "FRANCA";

            case "ITALIA":
            case "ITALY":
                return "ITALIA";

            case "ALEMANHA":
            case "GERMANY":
                return "ALEMANHA";

            case "ESPANHA":
            case "SPAIN":
                return "ESPANHA";

            case "INDIA":
                return "INDIA";

            case "CHINA":
                return "CHINA";

            case "JAPAO":
            case "JAPAN":
                return "JAPAO";

            case "AUSTRALIA":
                return "AUSTRALIA";

            case "POLONIA":
            case "POLAND":
                return "POLONIA";

            case "TURQUIA":
            case "TURKEY":
                return "TURQUIA";

            case "SUICA":
            case "SWITZERLAND":
                return "SUICA";

            default:
                return EMPTY;
        }
    }

    /*
     * ================================================================
     * LOTE
     * ================================================================
     */

    private static String extrairLote(
            List<String> lines
    ) {

        for (
                int i = 0;
                i < lines.size();
                i++
        ) {

            String n =
                    normalizar(
                            lines.get(
                                    i
                            )
                    );

            Matcher matcher =
                    Pattern.compile(
                                    "(?i)\\b(?:LOTE|LOT(?:\\s+CODE)?|BATCH)\\b"
                                            + "\\s*[:#]\\s*"
                                            + "([A-Z0-9][A-Z0-9./-]{1,20})\\b"
                            )
                            .matcher(n);

            if (!matcher.find()) {
                // Alguns rótulos colocam o código na linha seguinte ao heading.
                if (ehHeadingLoteIsolado(n) && i + 1 < lines.size()) {
                    String next = normalizar(lines.get(i + 1));
                    if (ehValorLoteValido(next)) {
                        return next;
                    }
                }
                continue;
            }

            String value =
                    matcher.group(
                            1
                    ).trim();

            if (
                    ehValorLoteValido(
                            value
                    )
            ) {

                return value;
            }

        }

        return EMPTY;
    }

    private static boolean ehValorLoteValido(
            String value
    ) {

        if (value == null || value.length() < 3 || value.length() > 20) {
            return false;
        }

        String n =
                normalizar(
                        value
                );

        if (n.startsWith("WWW")
                || n.startsWith("HTTP")
                || n.contains("EXPIR")
                || n.contains("VALID")
                || n.contains("VENC")
                || n.contains("DATE")
                || n.contains("MFG")
                || n.contains("MANUF")
                || n.equals("DOVE")
                || n.equals("DEL")
                || n.equals("ENVASE")
                || n.equals("FONDO")
                || n.equals("COM")
                || n.equals("AR")) {

            return false;
        }

        if (n.matches("^[0-9]{8,14}$")) {
            return false;
        }

        // Um lote exclusivamente alfabético é muito propenso a ser texto OCR.
        boolean hasLetter = n.matches(".*[A-Z].*");
        boolean hasDigit = n.matches(".*\\d.*");
        return (hasLetter && hasDigit)
                || n.matches("^[0-9]{3,7}$");
    }

    private static boolean ehHeadingLoteIsolado(String value) {
        return "LOTE".equals(value)
                || "LOT".equals(value)
                || "LOT CODE".equals(value)
                || "BATCH".equals(value);
    }

    /*
     * ================================================================
     * REGISTRO
     * ================================================================
     */

    private static String extrairRegistro(
            List<String> lines
    ) {

        LinkedHashSet<String> values =
                new LinkedHashSet<>();

        for (
                String line
                : lines
        ) {

            String n =
                    normalizar(
                            line
                    );

            if (
                    !REGISTRATION_MARKER_PATTERN
                            .matcher(
                                    n
                            )
                            .find()
            ) {
                continue;
            }

            Matcher matcher =
                    REGISTRATION_VALUE_PATTERN
                            .matcher(
                                    n
                            );

            while (
                    matcher.find()
            ) {

                String value =
                        compactarEspacos(
                                matcher.group()
                        ).trim();

                if (!value.isEmpty()) {

                    values.add(
                            value
                    );
                }
            }
        }

        /*
         * Marcador em uma linha e valor na próxima.
         */
        if (values.isEmpty()) {

            for (
                    int i = 0;
                    i < lines.size();
                    i++
            ) {

                if (
                        !REGISTRATION_MARKER_PATTERN
                                .matcher(
                                        normalizar(
                                                lines.get(
                                                        i
                                                )
                                        )
                                )
                                .find()
                ) {
                    continue;
                }

                if (
                        i + 1
                                < lines.size()
                ) {

                    Matcher matcher =
                            REGISTRATION_VALUE_PATTERN
                                    .matcher(
                                            normalizar(
                                                    lines.get(
                                                            i + 1
                                                    )
                                            )
                                    );

                    if (matcher.find()) {

                        values.add(
                                matcher.group()
                                        .trim()
                        );
                    }
                }
            }
        }

        return String.join(
                " | ",
                values
        );
    }

    /*
     * ================================================================
     * CONTEÚDO
     * ================================================================
     */

    private static String extrairConteudo(
            List<String> lines
    ) {

        /*
         * 1) Contextual.
         */
        for (
                String line
                : lines
        ) {

            String n =
                    normalizar(
                            line
                    );

            if (
                    !(
                            n.contains(
                                    "CONTEUDO"
                            )
                                    || n.contains(
                                    "PESO LIQUIDO"
                            )
                                    || n.contains(
                                    "VOLUME"
                            )
                    )
            ) {
                continue;
            }

            Matcher m =
                    NET_CONTENT_PATTERN.matcher(
                            n
                    );

            while (m.find()) {
                if (conteudoPlausivel(m.group(1))) {
                    return formatarConteudo(m.group(1), m.group(2));
                }
            }
        }

        /*
         * 2) Tamanho solto, somente quando parece conteúdo.
         */
        for (
                String line
                : lines
        ) {

            String n =
                    normalizar(
                            line
                    );

            if (
                    temContextoDePressaoOuAdministrativo(
                            n
                    )
            ) {
                continue;
            }

            Matcher m =
                    GENERIC_SIZE_PATTERN.matcher(
                            n
                    );

            while (m.find()) {
                if (linhaPareceDeConteudo(n) && conteudoPlausivel(m.group(1))) {
                    return formatarConteudo(m.group(1), m.group(2));
                }
            }
        }

        return EMPTY;
    }

    private static boolean conteudoPlausivel(String value) {
        // Long numeric package codes are not a retail cosmetic quantity.
        return value.matches("\\d{1,5}(?:[.,]\\d{1,3})?")
                && Double.parseDouble(value.replace(',', '.')) > 0;
    }

    private static boolean linhaPareceDeConteudo(
            String line
    ) {

        return line.length() <= 55
                || line.contains(
                "CAP"
        )
                || line.contains(
                "CONTE"
        )
                || line.contains(
                "ML"
        );
    }

    private static boolean temContextoDePressaoOuAdministrativo(
            String n
    ) {

        return n.contains(
                "PRESSAO"
        )
                || n.contains(
                "PRESION"
        )
                || n.contains(
                "CNPJ"
        )
                || n.contains(
                "AUTFUNC"
        )
                || n.contains(
                "REGISTRO"
        )
                || n.contains(
                "CEP"
        );
    }

    private static String formatarConteudo(
            String value,
            String unit
    ) {

        String u =
                normalizar(
                        unit
                );

        if (
                "MI".equals(
                        u
                )
                        || "M1".equals(
                        u
                )
        ) {

            u =
                    "ML";
        }

        if (
                "GR".equals(
                        u
                )
        ) {

            u =
                    "G";
        }

        return value.replace(
                ',',
                '.'
        )
                + " "
                + u;
    }

    /*
     * ================================================================
     * BARCODE
     * ================================================================
     */

    private static String extrairBarcode(
            List<String> lines
    ) {

        for (
                String line
                : lines
        ) {

            String n =
                    normalizar(
                            line
                    );

            Matcher matcher =
                    BARCODE_PATTERN.matcher(
                            n
                    );

            while (
                    matcher.find()
            ) {

                String candidate =
                        matcher.group(
                                1
                        );

                if (
                        validarChecksumEAN(
                                candidate
                        )
                ) {

                    return candidate;
                }
            }
        }

        return EMPTY;
    }

    private static boolean validarChecksumEAN(
            String code
    ) {

        if (code == null) {
            return false;
        }

        String digits =
                code.replaceAll(
                        "\\D",
                        EMPTY
                );

        if (
                !(
                        digits.length() == 8
                                || digits.length() == 12
                                || digits.length() == 13
                                || digits.length() == 14
                )
        ) {
            return false;
        }

        int sum =
                0;

        int positionFromRight =
                0;

        for (
                int i = digits.length() - 2;
                i >= 0;
                i--,
                        positionFromRight++
        ) {

            int d =
                    Character.digit(
                            digits.charAt(
                                    i
                            ),
                            10
                    );

            sum +=
                    (
                            positionFromRight % 2
                                    == 0
                    )
                            ? d * 3
                            : d;
        }

        int check =
                (10 - (
                        sum % 10
                )) % 10;

        return check
                == Character.digit(
                digits.charAt(
                        digits.length() - 1
                ),
                10
        );
    }

    /*
     * ================================================================
     * USO
     * ================================================================
     */

    private static String extrairUso(
            List<String> lines,
            IngredientSection section
    ) {

        /*
         * Heading explícito.
         */
        for (
                int i = 0;
                i < lines.size();
                i++
        ) {

            HeadingMatch heading =
                    encontrarHeading(
                            normalizar(
                                    lines.get(
                                            i
                                    )
                            ),
                            USAGE_HEADINGS,
                            true
                    );

            if (heading == null) {
                continue;
            }

            List<String> block =
                    new ArrayList<>();

            String remainder =
                    limparRestoHeading(
                            substringSafe(
                                    lines.get(
                                            i
                                    ),
                                    heading.endInOriginal
                            )
                    );

            if (
                    !remainder.isEmpty()
            ) {

                block.add(
                        remainder
                );
            }

            for (
                    int j = i + 1;
                    j < lines.size();
                    j++
            ) {

                String n =
                        normalizar(
                                lines.get(
                                        j
                                )
                        );

                if (
                        ehNovoBloco(
                                n,
                                INGREDIENT_HEADINGS
                        )
                                || ehNovoBloco(
                                n,
                                PRECAUTION_HEADINGS
                        )
                                || pareceAdministrativoForte(
                                n
                        )
                ) {
                    break;
                }

                block.add(
                        lines.get(
                                j
                        )
                );
            }

            String value =
                    limparBloco(
                            block,
                            SECURITY_TOKENS,
                            false
                    );

            value = limparUsoExtraido(value);

            if (!value.isEmpty()) {
                return value;
            }
        }

        /*
         * Fallback OCR.
         */
        List<String> fallback =
                new ArrayList<>();

        boolean collecting =
                false;

        for (
                int i = 0;
                i < lines.size();
                i++
        ) {

            String n =
                    normalizar(
                            lines.get(
                                    i
                            )
                    );

            if (
                    section.startIndex >= 0
                            && i >= section.startIndex
            ) {
                break;
            }

            if (
                    !collecting
                            && (
                            contémTermoComFronteira(
                                    n,
                                    "AGITE ANTES"
                            )
                                    || contémTermoComFronteira(
                                    n,
                                    "AGITAR ANTES"
                            )
                                    || contémTermoComFronteira(
                                    n,
                                    "MODO DE USAR"
                            )
                                    || contémTermoComFronteira(
                                    n,
                                    "MODO DE USO"
                            )
                                    || contémTermoComFronteira(n, "APPLY")
                                    || contémTermoComFronteira(n, "SPRAY")
                                    || contémTermoComFronteira(n, "SHAKE")
                                    || contémTermoComFronteira(n, "USE")
                    )
            ) {

                collecting =
                        true;

                int marker =
                        n.indexOf(
                                "AGITE ANTES"
                        );

                if (marker < 0) {

                    marker =
                            n.indexOf(
                                    "AGITAR ANTES"
                            );
                }

                if (marker < 0) {

                    marker =
                            n.indexOf(
                                    "MODO DE USAR"
                            );
                }

                if (marker < 0) {
                    marker = n.indexOf("MODO DE USO");
                }
                if (marker < 0) marker = n.indexOf("APPLY");
                if (marker < 0) marker = n.indexOf("SPRAY");
                if (marker < 0) marker = n.indexOf("SHAKE");
                if (marker < 0) marker = n.indexOf("USE");

                String action =
                        marker >= 0
                                && marker
                                < lines.get(
                                i
                        ).length()
                                ? lines.get(
                                i
                        ).substring(
                                Math.min(
                                        marker,
                                        lines.get(
                                                i
                                        ).length()
                                )
                        ).trim()
                                : lines.get(
                                i
                        );

                fallback.add(
                        action
                );

                continue;
            }

            if (!collecting) {
                continue;
            }

            /*
             * Mudança de responsabilidade para segurança.
             */
            if (
                    n.contains(
                            "PROTEGER OS OLHOS"
                    )
                            || n.contains(
                            "PROTEGER LOS OJOS"
                    )
                            || n.contains(
                            "NAO APLICAR"
                    )
                            || n.contains(
                            "NO APLICAR"
                    )
                            || n.contains(
                            "NAO USAR"
                    )
                            || n.contains(
                            "NO USAR"
                    )
            ) {

                break;
            }

            if (
                    temQualquerToken(
                            n,
                            new String[]{
                                    "PULVERIZAR",
                                    "APLICAR",
                                    "USAR",
                                    "UTILIZAR",
                                    "APPLY",
                                    "SPRAY",
                                    "SHAKE",
                                    "USE"
                            }
                    )
            ) {

                fallback.add(
                        lines.get(
                                i
                        )
                );
            }

            if (fallback.size() >= 2) {
                break;
            }
        }

        return limparUsoExtraido(
                limparBloco(
                        fallback,
                        new String[0],
                        false
                )
        );
    }

    private static String limparUsoExtraido(String value) {
        if (value == null || value.trim().isEmpty()) {
            return EMPTY;
        }

        String result = value.trim();
        Matcher ingredientStart = USAGE_INGREDIENT_START_PATTERN.matcher(result);
        if (ingredientStart.find()) {
            result = result.substring(0, ingredientStart.start()).trim();
        }

        Matcher contact = CONTACT_CONTEXT_PATTERN.matcher(result);
        if (contact.find()) {
            result = result.substring(0, contact.start()).trim();
        }

        return compactarEspacos(result);
    }

    /*
     * ================================================================
     * SEGURANÇA
     * ================================================================
     */

    private static String extrairSeguranca(
            List<String> lines,
            boolean warnings,
            IngredientSection section
    ) {

        List<String> explicit =
                new ArrayList<>();

        boolean collecting =
                false;

        for (
                int i = 0;
                i < lines.size();
                i++
        ) {

            String n =
                    normalizar(
                            lines.get(
                                    i
                            )
                    );

            boolean heading =
                    containsHeading(
                            n,
                            warnings
                                    ? new String[]{
                                    "WARNING",
                                    "WARNINGS",
                                    "ADVERTENCIA",
                                    "ADVERTENCIAS",
                                    "AVISOS",
                                    "DANGER",
                                    "PELIGRO"
                            }
                                    : PRECAUTION_HEADINGS
                    );

            if (heading) {

                collecting =
                        true;

                String remainder =
                        limparRestoHeading(
                                lines.get(
                                        i
                                )
                        );

                if (
                        !remainder.isEmpty()
                ) {

                    explicit.add(
                            remainder
                    );
                }

                continue;
            }

            if (collecting) {

                if (
                        section.startIndex >= 0
                                && i >= section.startIndex
                ) {
                    break;
                }

                if (
                        pareceAdministrativoForte(
                                n
                        )
                ) {
                    break;
                }

                explicit.add(
                        lines.get(
                                i
                        )
                );
            }
        }

        String explicitValue =
                limparBloco(
                        explicit,
                        new String[0],
                        false
                );

        if (
                !explicitValue.isEmpty()
        ) {

            return limitarTexto(
                    explicitValue,
                    1400
            );
        }

        /*
         * Fallback por frases.
         */
        List<String> safety =
                new ArrayList<>();

        for (
                String line
                : lines
        ) {

            String n =
                    normalizar(
                            line
                    );

            if (
                    !temQualquerToken(
                            n,
                            SECURITY_TOKENS
                    )
            ) {
                continue;
            }

            safety.add(
                    line
            );
        }

        String result =
                limparBloco(
                        safety,
                        new String[0],
                        false
                );

        if (warnings) {

            List<String> selected =
                    new ArrayList<>();

            for (
                    String line
                    : safety
            ) {

                String n =
                        normalizar(
                                line
                        );

                if (
                        n.contains(
                                "PRESSAO"
                        )
                                || n.contains(
                                "PRESION"
                        )
                                || n.contains(
                                "EXPLO"
                        )
                                || n.contains(
                                "INCINER"
                        )
                                || n.contains(
                                "FOGO"
                        )
                                || n.contains(
                                "LLAMA"
                        )
                                || n.contains(
                                "OLHOS"
                        )
                                || n.contains(
                                "OJOS"
                        )
                                || n.contains(
                                "DANGER"
                        )
                                || n.contains(
                                "WARNING"
                        )
                                || n.contains(
                                "PELIGRO"
                        )
                ) {

                    selected.add(
                            line
                    );
                }
            }

            String selectedText =
                    limparBloco(
                            selected,
                            new String[0],
                            false
                    );

            return limitarTexto(
                    selectedText.isEmpty()
                            ? result
                            : selectedText,
                    1400
            );
        }

        return limitarTexto(
                result,
                1800
        );
    }

    /*
     * ================================================================
     * CLAIMS
     * ================================================================
     */

    private static List<String> extrairClaims(
            List<String> lines
    ) {

        LinkedHashSet<String> claims =
                new LinkedHashSet<>();

        for (
                String line
                : lines
        ) {

            String n =
                    normalizar(
                            line
                    );

            for (
                    String token
                    : CLAIM_TOKENS
            ) {

                if (
                        contémTermoComFronteira(
                                n,
                                token
                        )
                ) {

                    claims.add(
                            limitarTexto(
                                    compactarEspacos(
                                            line.trim()
                                    ),
                                    180
                            )
                    );

                    break;
                }
            }
        }

        return new ArrayList<>(
                claims
        );
    }

    /*
     * ================================================================
     * OTHER TEXT
     * ================================================================
     */

    private static String extrairOtherText(
            List<String> lines,
            IngredientSection section
    ) {

        List<String> other =
                new ArrayList<>();

        for (
                int i = 0;
                i < lines.size();
                i++
        ) {

            if (
                    i == section.startIndex
                            || (
                            section.startIndex >= 0
                                    && i
                                    > section.startIndex
                                    && i
                                    <= section.endIndex
                    )
            ) {
                continue;
            }

            String line =
                    compactarEspacos(
                            lines.get(
                                    i
                            )
                    );

            if (line.isEmpty()) {
                continue;
            }

            if (
                    pareceAdministrativoForte(
                            normalizar(
                                    line
                            )
                    )
            ) {
                continue;
            }

            if (
                    containsAnyClaim(
                            normalizar(
                                    line
                            )
                    )
            ) {
                continue;
            }

            if (
                    ehTextoMuitoCurto(
                            line
                    )
            ) {
                continue;
            }

            other.add(
                    line
            );
        }

        return limitarTexto(
                String.join(
                        " | ",
                        other
                ),
                2200
        );
    }

    /*
     * ================================================================
     * HELPERS
     * ================================================================
     */

    private static String joinLines(
            List<String> lines
    ) {

        if (
                lines == null
                        || lines.isEmpty()
        ) {
            return EMPTY;
        }

        List<String> clean =
                new ArrayList<>();

        for (
                String line
                : lines
        ) {

            if (
                    line != null
                            && !line.trim().isEmpty()
            ) {

                clean.add(
                        line.trim()
                );
            }
        }

        return String.join(
                "\n",
                clean
        );
    }

    private static List<String> normalizarLinhas(
            List<String> source
    ) {

        if (source == null) {
            return Collections.emptyList();
        }

        List<String> result =
                new ArrayList<>();

        for (
                String line
                : source
        ) {

            if (line == null) {
                continue;
            }

            String clean =
                    line
                            .replace(
                                    '\u0000',
                                    ' '
                            )
                            .replaceAll(
                                    "[\\t\\r]+",
                                    " "
                            )
                            .replaceAll(
                                    "\\s+",
                                    " "
                            )
                            .trim();

            if (!clean.isEmpty()) {

                result.add(
                        clean
                );
            }
        }

        return result;
    }

    private static String normalizarOCRBasico(
            String value
    ) {

        if (value == null) {
            return EMPTY;
        }

        return value
                .replace(
                        '\u0000',
                        ' '
                )
                .replace(
                        '’',
                        '\''
                )
                .replace(
                        '“',
                        '"'
                )
                .replace(
                        '”',
                        '"'
                )
                .replace(
                        '–',
                        '-'
                )
                .replace(
                        '—',
                        '-'
                )
                .replaceAll(
                        "[\\t\\r\\n]+",
                        " "
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    private static String normalizar(
            String value
    ) {

        if (value == null) {
            return EMPTY;
        }

        String text =
                Normalizer
                        .normalize(
                                value,
                                Normalizer.Form.NFD
                        )
                        .replaceAll(
                                "\\p{InCombiningDiacriticalMarks}+",
                                EMPTY
                        )
                        .toUpperCase(
                                Locale.ROOT
                        )
                        .replace(
                                'Ç',
                                'C'
                        );

        return text
                .replaceAll(
                        "[^A-Z0-9./:%+@#&' -]",
                        " "
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    private static String compactarEspacos(
            String value
    ) {

        return value == null
                ? EMPTY
                : value
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    private static String limparPontosFinais(
            String value
    ) {

        if (value == null) {
            return EMPTY;
        }

        return value
                .replaceAll(
                        "[. ]+$",
                        ""
                )
                .trim();
    }

    private static String substringSafe(
            String value,
            int start
    ) {

        if (
                value == null
                        || start >= value.length()
        ) {
            return EMPTY;
        }

        return value.substring(
                Math.max(
                        0,
                        start
                )
        );
    }

    private static String safeSubstring(
            String value,
            int start,
            int end
    ) {

        if (value == null) {
            return EMPTY;
        }

        int s =
                Math.max(
                        0,
                        Math.min(
                                start,
                                value.length()
                        )
                );

        int e =
                Math.max(
                        s,
                        Math.min(
                                end,
                                value.length()
                        )
                );

        return value.substring(
                s,
                e
        );
    }

    private static String limparRestoHeading(
            String value
    ) {

        if (value == null) {
            return EMPTY;
        }

        return value
                .replaceFirst(
                        "^[\\s:;.,#\\-]+",
                        EMPTY
                )
                .trim();
    }

    private static HeadingMatch encontrarHeading(
            String normalizedLine,
            String[] headings,
            boolean allowFuzzy
    ) {

        if (normalizedLine == null) {
            return null;
        }

        String line =
                compactarEspacos(
                        normalizar(
                                normalizedLine
                        )
                );

        for (
                String heading
                : headings
        ) {

            String h =
                    normalizar(
                            heading
                    );

            int idx =
                    indexOfMarkerWithBoundary(
                            line,
                            h
                    );

            if (idx >= 0) {

                return new HeadingMatch(
                        heading,
                        idx + h.length()
                );
            }
        }

        /*
         * Fuzzy somente para heading longo.
         */
        if (
                !allowFuzzy
                        || line.length() > 80
        ) {
            return null;
        }

        String[] tokens =
                line.split(
                        " "
                );

        for (
                String heading
                : headings
        ) {

            String h =
                    normalizar(
                            heading
                    );

            String[] ht =
                    h.split(
                            " "
                    );

            if (
                    ht.length == 1
                            && ht[0].length() >= 7
            ) {

                for (
                        String token
                        : tokens
                ) {

                    if (
                            token.length() >= 6
                                    && levenshtein(
                                    token,
                                    ht[0]
                            ) <= 1
                    ) {

                        return new HeadingMatch(
                                heading,
                                line.indexOf(
                                        token
                                )
                                        + token.length()
                        );
                    }
                }
            }
        }

        return null;
    }

    private static boolean containsHeading(
            String line,
            String[] headings
    ) {

        return encontrarHeading(
                line,
                headings,
                true
        ) != null;
    }

    private static boolean ehNovoBloco(
            String line,
            String[] headings
    ) {

        return containsHeading(
                line,
                headings
        );
    }

    private static boolean pareceAdministrativoForte(
            String line
    ) {

        if (
                line == null
                        || line.isEmpty()
        ) {
            return false;
        }

        String n =
                normalizar(
                        line
                );

        if (
                CNPJ_PATTERN
                        .matcher(
                                n
                        )
                        .find()
                        || CEP_PATTERN
                        .matcher(
                                n
                        )
                        .find()
        ) {
            return true;
        }

        if (
                PHONE_PATTERN
                        .matcher(
                                n
                        )
                        .find()
                        && !n.matches(
                        ".*\\b(?:AQUA|WATER|ALCOHOL)\\b.*"
                )
        ) {
            /*
             * Telefone isolado é administrativo.
             */
            return true;
        }

        for (
                String token
                : ADMIN_GARBAGE
        ) {

            if (
                    contémTermoComFronteira(
                            n,
                            token
                    )
            ) {

                return true;
            }
        }

        return false;
    }

    private static boolean ehTextoAdministrativoObvio(
            String value
    ) {

        if (value == null) {
            return false;
        }

        String n =
                normalizar(
                        value
                );

        return CNPJ_PATTERN
                .matcher(n)
                .find()
                || CEP_PATTERN
                .matcher(n)
                .find()
                || CONTACT_CONTEXT_PATTERN.matcher(n).find()
                || temContextoAdministrativo(n);
    }

    private static boolean temContextoAdministrativo(
            String n
    ) {

        if (n == null) {
            return false;
        }

        for (
                String token
                : ADMIN_GARBAGE
        ) {

            if (
                    contémTermoComFronteira(
                            n,
                            token
                    )
            ) {

                return true;
            }
        }

        return false;
    }

    private static boolean temRuidoAdministrativo(
            String n
    ) {

        return temContextoAdministrativo(
                n
        )
                || n.contains(
                "CONTEUDO SOB PRESSAO"
        )
                || n.contains(
                "PROTEGER OS OLHOS"
        )
                || n.contains(
                "ATENDIMENTO AO CONSUMIDOR"
        );
    }

    private static boolean temRuidoAdministrativoExcessivo(
            String line
    ) {

        String n =
                normalizar(
                        line
                );

        int hits =
                0;

        for (
                String token
                : ADMIN_GARBAGE
        ) {

            if (
                    contémTermoComFronteira(
                            n,
                            token
                    )
            ) {

                hits++;
            }
        }

        return hits >= 2;
    }

    private static boolean pareceImportacao(
            List<String> lines,
            int index
    ) {

        int from =
                Math.max(
                        0,
                        index - 5
                );

        int to =
                Math.min(
                        lines.size() - 1,
                        index + 1
                );

        for (
                int i = from;
                i <= to;
                i++
        ) {

            String n =
                    normalizar(
                            lines.get(
                                    i
                            )
                    );

            for (
                    String marker
                    : IMPORTER_MARKERS
            ) {

                if (
                        contémTermoComFronteira(
                                n,
                                marker
                        )
                ) {

                    return true;
                }
            }
        }

        return false;
    }

    private static boolean pareceDistribuicao(
            List<String> lines,
            int index
    ) {

        int from =
                Math.max(
                        0,
                        index - 3
                );

        int to =
                Math.min(
                        lines.size() - 1,
                        index + 1
                );

        for (
                int i = from;
                i <= to;
                i++
        ) {

            String n =
                    normalizar(
                            lines.get(
                                    i
                            )
                    );

            for (
                    String marker
                    : DISTRIBUTOR_MARKERS
            ) {

                if (
                        contémTermoComFronteira(
                                n,
                                marker
                        )
                ) {

                    return true;
                }
            }
        }

        return false;
    }

    private static boolean contemMarcadorCorporativo(
            String line
    ) {

        String n =
                normalizar(
                        line
                );

        for (
                String marker
                : CORPORATE_MARKERS
        ) {

            if (
                    contémTermoComFronteira(
                            n,
                            marker
                    )
            ) {

                return true;
            }
        }

        return false;
    }

    private static boolean temSufixoEmpresarial(
            String line
    ) {

        String n =
                normalizar(
                        line
                );

        for (
                String suffix
                : COMPANY_SUFFIXES
        ) {

            if (
                    n.matches(
                            ".*\\b"
                                    + Pattern.quote(
                                    normalizar(
                                            suffix
                                    )
                            )
                                    + "\\b.*"
                    )
            ) {

                return true;
            }
        }

        return false;
    }

    private static int indexOfMarkerWithBoundary(
            String text,
            String marker
    ) {

        if (
                text == null
                        || marker == null
                        || marker.isEmpty()
        ) {
            return -1;
        }

        int idx =
                text.indexOf(
                        marker
                );

        while (
                idx >= 0
        ) {

            int end =
                    idx
                            + marker.length();

            boolean left =
                    idx == 0
                            || !Character.isLetterOrDigit(
                            text.charAt(
                                    idx - 1
                            )
                    );

            boolean right =
                    end >= text.length()
                            || !Character.isLetterOrDigit(
                            text.charAt(
                                    end
                            )
                    );

            if (
                    left
                            && right
            ) {

                return idx;
            }

            idx =
                    text.indexOf(
                            marker,
                            idx + 1
                    );
        }

        return -1;
    }

    private static int indexOfAnyBoundary(
            String text,
            String[] markers
    ) {

        int best =
                -1;

        for (
                String marker
                : markers
        ) {

            int idx =
                    indexOfMarkerWithBoundary(
                            text,
                            normalizar(
                                    marker
                            )
                    );

            if (
                    idx >= 0
                            && (
                            best < 0
                                    || idx < best
                    )
            ) {

                best =
                        idx;
            }
        }

        return best;
    }

    private static boolean contémTermoComFronteira(
            String text,
            String term
    ) {

        if (
                text == null
                        || term == null
        ) {
            return false;
        }

        String t =
                normalizar(
                        term
                );

        String n =
                normalizar(
                        text
                );

        return indexOfMarkerWithBoundary(
                n,
                t
        ) >= 0;
    }

    private static boolean temQualquerToken(
            String line,
            String[] tokens
    ) {

        for (
                String token
                : tokens
        ) {

            if (
                    contémTermoComFronteira(
                            line,
                            token
                    )
            ) {

                return true;
            }
        }

        return false;
    }

    private static boolean containsAnyClaim(
            String line
    ) {

        return temQualquerToken(
                line,
                CLAIM_TOKENS
        );
    }

    private static String limparLinhaIngredientes(
            String line
    ) {

        String n =
                normalizarOCRBasico(
                        line
                );

        /*
         * Mantemos pontuação interna.
         * Ela ainda pode ser necessária para segmentação.
         */
        return n
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    private static String limparBloco(
            List<String> lines,
            String[] ignoredTokens,
            boolean onlyActionLines
    ) {

        if (
                lines == null
                        || lines.isEmpty()
        ) {
            return EMPTY;
        }

        List<String> valid =
                new ArrayList<>();

        for (
                String line
                : lines
        ) {

            if (line == null) {
                continue;
            }

            String n =
                    normalizar(
                            line
                    );

            if (n.isEmpty()) {
                continue;
            }

            if (
                    pareceAdministrativoForte(
                            n
                    )
            ) {
                continue;
            }

            if (
                    onlyActionLines
                            && !temQualquerToken(
                            n,
                            new String[]{
                                    "APLICAR",
                                    "PULVERIZAR",
                                    "AGITE",
                                    "AGITAR",
                                    "USAR",
                                    "UTILIZAR",
                                    "DIRECTIONS",
                                    "HOW TO"
                            }
                    )
            ) {
                continue;
            }

            valid.add(
                    compactarEspacos(
                            line
                    )
            );
        }

        return compactarEspacos(
                String.join(
                        " ",
                        valid
                )
        );
    }

    private static boolean ehTextoMuitoCurto(
            String line
    ) {

        return line == null
                || line.trim().length() < 3;
    }

    private static int contarLetras(
            String value
    ) {

        int count =
                0;

        for (
                int i = 0;
                i < value.length();
                i++
        ) {

            if (
                    Character.isLetter(
                            value.charAt(
                                    i
                            )
                    )
            ) {

                count++;
            }
        }

        return count;
    }

    private static String limitarTexto(
            String value,
            int max
    ) {

        if (value == null) {
            return EMPTY;
        }

        String v =
                compactarEspacos(
                        value
                );

        return v.length() <= max
                ? v
                : v.substring(
                0,
                max
        ).trim();
    }

    private static List<String> deduplicarPreservandoOrdem(
            List<String> values
    ) {

        LinkedHashSet<String> set =
                new LinkedHashSet<>();

        for (
                String value
                : values
        ) {

            String clean =
                    compactarEspacos(
                            value
                    );

            if (!clean.isEmpty()) {

                set.add(
                        clean
                );
            }
        }

        return new ArrayList<>(
                set
        );
    }

    /*
     * ================================================================
     * ASSINATURA
     * ================================================================
     */

    private static String gerarAssinaturaOCR(
            List<ScanBackIngredientCandidate> ingredients
    ) {

        StringBuilder data =
                new StringBuilder();

        for (
                ScanBackIngredientCandidate ingredient
                : ingredients
        ) {

            data.append(
                    ingredient
                            .getNormalizedName()
            );

            data.append(
                    '|'
            );
        }

        return "OCR:"
                + sha256(
                data.toString()
        );
    }

    private static String sha256(
            String input
    ) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] bytes =
                    digest.digest(
                            input.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            StringBuilder hex =
                    new StringBuilder(
                            bytes.length * 2
                    );

            for (
                    byte b
                    : bytes
            ) {

                hex.append(
                        String.format(
                                Locale.ROOT,
                                "%02x",
                                b
                        )
                );
            }

            return hex.toString();

        } catch (
                NoSuchAlgorithmException e
        ) {

            return "UNAVAILABLE";
        }
    }

    /*
     * ================================================================
     * LEVENSHTEIN
     * ================================================================
     */

    private static int levenshtein(
            String a,
            String b
    ) {

        int[] prev =
                new int[
                        b.length() + 1
                        ];

        int[] curr =
                new int[
                        b.length() + 1
                        ];

        for (
                int j = 0;
                j <= b.length();
                j++
        ) {

            prev[j] =
                    j;
        }

        for (
                int i = 1;
                i <= a.length();
                i++
        ) {

            curr[0] =
                    i;

            for (
                    int j = 1;
                    j <= b.length();
                    j++
            ) {

                int cost =
                        a.charAt(
                                i - 1
                        )
                                == b.charAt(
                                j - 1
                        )
                                ? 0
                                : 1;

                curr[j] =
                        Math.min(
                                Math.min(
                                        curr[j - 1] + 1,
                                        prev[j] + 1
                                ),
                                prev[j - 1] + cost
                        );
            }

            int[] temp =
                    prev;

            prev =
                    curr;

            curr =
                    temp;
        }

        return prev[
                b.length()
                ];
    }

    /*
     * ================================================================
     * ESTRUTURAS PRIVADAS
     * ================================================================
     */


    private static final class IngredientSection {

        final boolean found;

        final int startIndex;

        final int endIndex;

        final String rawText;

        final String stopReason;

        IngredientSection(
                boolean found,
                int startIndex,
                int endIndex,
                String rawText,
                String stopReason
        ) {

            this.found =
                    found;

            this.startIndex =
                    startIndex;

            this.endIndex =
                    endIndex;

            this.rawText =
                    rawText == null
                            ? EMPTY
                            : rawText;

            this.stopReason =
                    stopReason == null
                            ? EMPTY
                            : stopReason;
        }
    }

    private static final class StopMatch {

        final String reason;

        final int positionInLine;

        StopMatch(
                String reason,
                int positionInLine
        ) {

            this.reason =
                    reason;

            this.positionInLine =
                    positionInLine;
        }
    }

    private static final class HeadingMatch {

        final String term;

        final int endInOriginal;

        HeadingMatch(
                String term,
                int endInOriginal
        ) {

            this.term =
                    term;

            this.endInOriginal =
                    endInOriginal;
        }
    }

    private static final class Boundary {

        final int start;

        final int end;

        final String value;

        Boundary(
                int start,
                int end,
                String value
        ) {

            this.start =
                    start;

            this.end =
                    end;

            this.value =
                    value;
        }
    }

    private static final class CompanyMarker {

        final String prefix;

        final String remainder;

        final String marker;

        final String normalizedMarker;

        CompanyMarker(
                String prefix,
                String remainder,
                String marker,
                String normalizedMarker
        ) {

            this.prefix =
                    prefix == null
                            ? EMPTY
                            : prefix;

            this.remainder =
                    remainder == null
                            ? EMPTY
                            : remainder;

            this.marker =
                    marker == null
                            ? EMPTY
                            : marker;

            this.normalizedMarker =
                    normalizedMarker == null
                            ? EMPTY
                            : normalizedMarker;
        }
    }
}
