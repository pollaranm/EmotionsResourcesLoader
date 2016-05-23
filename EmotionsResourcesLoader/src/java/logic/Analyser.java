package logic;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Properties;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.StrBuilder;
import org.json.*;
import com.vdurmont.emoji.Emoji;
import com.vdurmont.emoji.EmojiManager;
import com.vdurmont.emoji.EmojiParser;
import edu.stanford.nlp.ling.CoreAnnotations;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;

/**
 *
 * @author TTm
 */
public class Analyser extends HttpServlet {

    /**
     * Directory contenente le cartelle con i tweet suddivise per sentimento
     */
    File dirTweet = new File("C:/Dropbox/tweet_temp");

    /**
     * Lista delle cartelle 'sentimento' contenenti i tweets
     */
    File[] sentimentsFoldersList = dirTweet.listFiles();

    /**
     * HashMap dove verranno caricate le varie emoticon. Per ogniuna di queste è
     * presente una hash interna che conterrà il conteggio delle occorrenze
     * associato ad ogni sentimento
     */
    HashMap<String, HashMap<String, Integer>> emoticons = new HashMap<>();

    /**
     * HashMap dove verranno caricati i vari hashtag. Per ogniuno di questi è
     * presente una hash interna che conterrà il conteggio delle occorrenze
     * associato ad ogni sentimento
     */
    HashMap<String, HashMap<String, Integer>> hashtags = new HashMap<>();

    /**
     * HashMap di supporto per il conteggio delle occorrenze delle risorse
     * lessicali riscontrate nell'analisi dei tweet.
     */
    HashMap<String, HashMap<String, Integer>> oldWords = new HashMap<>();

    /**
     * HashMap di supporto per il salvataggio e conteggio delle nuove risorse
     * lessicali rinvenute durante l'analisi dei tweet.
     */
    HashMap<String, HashMap<String, Integer>> newWords = new HashMap<>();

    /**
     * HashMap dove verranno caricati i vari emoji. Per ogniuno di questi è
     * presente una hash interna che conterrà il conteggio delle occorrenze
     * associato ad ogni sentimento
     */
    HashMap<String, HashMap<String, Integer>> emoji = new HashMap<>();

    /**
     * HashMap contenente gli slang che andranno ricercati nei tweet e
     * sostituiti con la loro forma estesa
     */
    HashMap<String, String> slangs = new HashMap<>();

    /**
     * Lista delle stop-word che andranno eliminate dai tweet
     */
    LinkedList<String> stopwords = new LinkedList<String>();

    /**
     * Lista contenente i segni di punteggiatura che andranno eliminati dai file
     */
    LinkedList<String> punctuation = new LinkedList<String>();

    // ###################################################
    // ###             DATABASE DATA                   ###
    // ###################################################
    String myDriver = "oracle.jdbc.driver.OracleDriver";
    String myUrl = "jdbc:oracle:thin:@localhost:1521:oralab";
    String myUser = "USERTEST";
    String myPass = "app";
//    String myUrl = "jdbc:oracle:thin:@laboracle.educ.di.unito.it:1521:oralab";
//    String myUser = "sp138279";
//    String myPass = "testtest";

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        loadEmoticons();
        loadSlangs();
        loadStopwords();
        loadPunctuation();
        loadEmoji();

        // Per ogni cartella fa partire l'elaborazione di un 'sentimento'
        for (File sentimentFolder : sentimentsFoldersList) {
            System.out.println("------ SENTIMENT " + sentimentFolder.getName() + "------");
            elaborateSentiment(sentimentFolder);
        }
        System.out.println("\n\nElaboration Complete!\n\n");

//         Terminata la fase di elaborazione dei tweet, raccolti i risultati
//         nelle strutture dati temporanee si procede al salvataggio su DB
        storeResultsOperation();
    }

    private void elaborateSentiment(File sentimentFolder) {
        File[] sentimentTweetList = sentimentFolder.listFiles();
        oldWords.put(sentimentFolder.getName(), new HashMap<>());
        newWords.put(sentimentFolder.getName(), new HashMap<>());

        // mi trovo dentro la cartella di un sentimento, ciclo per ogni raccolta di tweet
        for (File tweetFile : sentimentTweetList) {
            System.out.println("###### Open res: " + tweetFile.getName() + " ######");
            try {
                Path path = Paths.get(tweetFile.getAbsolutePath());
                Path destination = Paths.get(sentimentFolder + File.separator + sentimentFolder.getName() + ".txt");

                Charset charset = StandardCharsets.UTF_8;
                byte[] contentByte = Files.readAllBytes(path);
                String contentString = new String(contentByte, charset);

//                contentString = removeTwitterWords(contentString);
//                contentString = processEmoticons(contentString, sentimentFolder.getName());
//                contentString = processSlangWords(contentString);
                contentString = processHashtags(contentString, sentimentFolder.getName());
//                contentString = removePunctuation(contentString);
//                contentString = processStopwords(contentString);
//                contentString = processEmoji(contentString, sentimentFolder.getName());

//                contentString = processWord(contentString, sentimentFolder.getName());
//                contentString = processStopwords(contentString);
//
//                Files.write(destination, contentString.getBytes(charset));
            } catch (IOException ex) {
                Logger.getLogger(Analyser.class
                        .getName()).log(Level.SEVERE, null, ex);
            }
            System.out.println("###### Close res: " + tweetFile.getName() + " ######");
        }
    }

    /**
     * Procedura di caricamento delle emoticons all'interno di una hash globale.
     * Ogni emoticons avrà associata un'ulteriore hash che per ogni sentimento
     * terrà il conteggio delle occorrenza riscontrate nei tweet.
     */
    private void loadEmoticons() {
        try {
            // Punta al file contenente l'elenco delle emoticons
            // File emoticonsFile = new File("C:/Dropbox/lex_util/emoticon_SHORT.txt");
            File emoticonsFile = new File("C:/Dropbox/lex_util/emoticon_LONG.txt");
            BufferedReader br = new BufferedReader(new FileReader(emoticonsFile.getAbsolutePath()));
            String sCurrentLine;
            // Analizza ogni riga del file l'aggiunge alla hash globale
            while ((sCurrentLine = br.readLine()) != null) {
                emoticons.putIfAbsent(sCurrentLine, new HashMap<String, Integer>());
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Analyser.class
                    .getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Analyser.class
                    .getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("Emoticons READY!");
    }

    /**
     * Precedura di precaricamento degli slang per la successiva sostituzione
     * all'interno dei tweet.
     */
    private void loadSlangs() {
        try {
            // Punta al file contenente l'elenco degli slang/abbreviazioni
            File slangFile = new File("C:/Dropbox/lex_util/slang.txt");
            BufferedReader br = new BufferedReader(new FileReader(slangFile.getAbsolutePath()));
            String sCurrentLine;

            // Analizza ogni riga del file l'aggiunge alla hash globale
            while ((sCurrentLine = br.readLine()) != null) {
                String[] parts = sCurrentLine.split(":");
                slangs.put(" " + parts[0] + " ", " " + parts[1] + " ");
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Analyser.class
                    .getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Analyser.class
                    .getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("Slang READY!");
    }

    /**
     * Carica in memoria i segni di punteggiatura ed alcuni simboli che poi
     * verranno rimossi.
     */
    private void loadPunctuation() {
        try {
            // Punta al file contenente l'elenco dei simboli da eliminare
            File puntactionFile = new File("C:/Dropbox/lex_util/punctuation.txt");
            BufferedReader br = new BufferedReader(new FileReader(puntactionFile.getAbsolutePath()));
            String sCurrentLine;

            // Analizza ogni riga del file l'aggiunge alla lista
            while ((sCurrentLine = br.readLine()) != null) {
                punctuation.add(sCurrentLine);
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Analyser.class
                    .getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Analyser.class
                    .getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("Punctuation READY!");
    }

    /**
     * Procedura di carimento da file delle stopwords che verranno eliminate
     * durante la fase di prepocessamento dei tweet.
     */
    private void loadStopwords() {
        try {
            // Punta al file contenente l'elenco delle stopword
            // File stopwordFile = new File("C:/Dropbox/lex_util/stopword_SHORT.txt");
            File stopwordFile = new File("C:/Dropbox/lex_util/stopword_LONG.txt");
            BufferedReader br = new BufferedReader(new FileReader(stopwordFile.getAbsolutePath()));
            String sCurrentLine;

            // Analizza ogni riga del file l'aggiunge alla hash globale
            while ((sCurrentLine = br.readLine()) != null) {
                stopwords.add(sCurrentLine);
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Analyser.class
                    .getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Analyser.class
                    .getName()).log(Level.SEVERE, null, ex);
        }
        System.out.println("Stopword READY!");
    }

    /**
     * Recupera tutte le possibili emoji presenti nella libreria e le carica in
     * memoria, predisponendo già le hashmap interne per il conteggio delle
     * occorrenze.
     */
    private void loadEmoji() {
        Collection<Emoji> allEmoji = EmojiManager.getAll();
        for (Emoji em : allEmoji) {
            emoji.putIfAbsent(":" + em.getAliases().get(0) + ":", new HashMap<String, Integer>());
        }
        System.out.println("Emoji READY!");
    }

    /**
     * Analizza il testo passato come input e ne rimuove le parole chiave
     * tipiche di un tweet 'USERNAME' e 'URL'.
     *
     * @param text Testo da analizzare
     * @return Testo ripulito delle parole 'USERNAME' e 'URL'
     */
    private String removeTwitterWords(String text) {
        System.out.print("TwitterWords ... ");
        text = text.replace("USERNAME", "");
        text = text.replace("URL", "");
        System.out.print("REMOVED");
        return text;
    }

    /**
     * Scandisce l'intero testo passato come paramentro alla ricerca delle
     * diverse emoticon presenti nell'hashmap globale 'emoticons'. Ogni emoticon
     * trovata verrà cancellata dal testo finale ma il conteggio totale delle
     * occorrenze per sentimento verrà salvato sempre nella relatica hashmap.
     *
     * @param text Testo da analizzare
     * @param sentiment Sentimento associato al testo analizzato
     * @return Il testo ripulito delle emoticons
     */
    private String processEmoticons(String text, String sentiment) {
        System.out.print("Emoticons ... ");
        for (Map.Entry emoticon : emoticons.entrySet()) {
            Integer tempCont = StringUtils.countMatches(text, (String) emoticon.getKey());
            HashMap<String, Integer> tempHash = emoticons.get((String) emoticon.getKey());
            tempHash.put(sentiment, tempCont);
            emoticons.put((String) emoticon.getKey(), tempHash);
        }
        for (Map.Entry emoticon : emoticons.entrySet()) {
            text = text.replace((String) emoticon.getKey(), "");
        }
        System.out.print("PROCESSED");
        return text;
    }

    /**
     * Sostituisce tutte le forme abbreviate e/o slang presenti nel testo con la
     * loro relativa forma per esteso. Si appoggia alla precendente funzione di
     * caricamento da file di questi slang 'loadSlang()'.
     *
     * @param text Testo da elaborare
     * @return Il testo passato in input arricchito con le forme estese degli
     * slang
     */
    private String processSlangWords(String text) {
        System.out.print("Slang ... ");
        text = text.replace(System.lineSeparator(), " :-EOL-: ");
        for (Map.Entry temp : slangs.entrySet()) {
            String slangForm = (String) temp.getKey();
            String extendedForm = (String) temp.getValue();
            text = text.replace(slangForm, extendedForm);
        }
        text = text.replace(" :-EOL-: ", System.lineSeparator());
        System.out.print("PROCESSED");
        return text;
    }

    /**
     * Rimuove tutte le stopwords presenti nel testo passato in input. L'elenco
     * delle parole da eliminare è definito tramite il metodo 'loadStopwords()'.
     *
     * @param text Testo originale da cui bisogna togliere le stopwords
     * @return Testo ripulito dalle stopwords presenti in elenco
     */
    private String processStopwords(String text) {
        System.out.print("Stopwords ... ");
        StrBuilder elaboratedText = new StrBuilder();
        try {
            BufferedReader br = new BufferedReader(new StringReader(text));
            String sCurrentLine;
            while ((sCurrentLine = br.readLine()) != null) {
                String[] splittedText = sCurrentLine.split(" ");
                for (String token : splittedText) {
                    if ((token.length() > 2) && !stopwords.contains(token)) {
                        elaboratedText.append(" " + token);
                    }
                }
                elaboratedText.appendNewLine();
            }
        } catch (IOException ex) {
            Logger.getLogger(Analyser.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.print("REMOVED");
        return elaboratedText.toString();
    }

    /**
     * Procedura di elaborazione degi hashtag presenti nel testo passato come
     * parametro. Durante la scansione vengono salvati e conteggiati nella
     * hashmap 'hashtags', suddivisa a sua volta in una sottomappa per
     * sentimento.
     *
     * @param text Il testo da analizzare
     * @param sentiment Il sentimento associato al testo analizzato
     * @return Il testo privo degli hashtag
     */
    private String processHashtags(String text, String sentiment) {
        System.out.print("Hashtag ... ");
        StrBuilder elaboratedText = new StrBuilder();
        try {
            BufferedReader br = new BufferedReader(new StringReader(text));
            String sCurrentLine;
            while ((sCurrentLine = br.readLine()) != null) {
                String[] splittedText = sCurrentLine.split(" ");
                for (String token : splittedText) {
                    if (token.length() > 1 && isHashTag(token)) {
                        elaborateHashtag(token, sentiment);
                    } else {
                        elaboratedText.append(" " + token);
                    }
                }
                elaboratedText.appendNewLine();
            }
        } catch (IOException ex) {
            Logger.getLogger(Analyser.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.print("PROCESSED");
        return elaboratedText.toString();
    }

    /**
     * Procedura di salvataggio e conteggio delle occorrenze degli hashtag.
     * Viene invocato quando nella procedura di analisi degli hashtag, si
     * riscontra un'occorrenza: a questo punto se è già presente si aggiorna il
     * conteggio, altrimenti si inizializza. Il conteggio viene fatto
     * considerando in quale 'sentimento' viene trovato l'hashtag.
     *
     * @param word Hashtag che si vuole valutare
     * @param sentiment Sentimento relativo all'occorrenza da registrare
     */
    private void elaborateHashtag(String word, String sentiment) {
        if (hashtags.containsKey(word)) {
            // se l'hashtag è già stato registrato
            if (hashtags.get(word).containsKey(sentiment)) {
                // se non è la prima volta che lo osservo in questo sentimento, aggiorno
                HashMap<String, Integer> old = hashtags.get(word);
                old.replace(sentiment, old.get(sentiment) + 1);
                hashtags.replace(word, old);
            } else {
                // se è la prima volta che lo osservo in questo sentimento, lo creo
                HashMap<String, Integer> temp = hashtags.get(word);
                temp.put(sentiment, 1);
                hashtags.put(word, temp);
            }
        } else {
            // se l'hashtag è nuovo
            HashMap<String, Integer> first = new HashMap<>();
            first.put(sentiment, 1);
            hashtags.put(word, first);
        }
    }

    /**
     * Controlla se la parola potrebbe essere un hashtag, valutando il primo
     * carattere.
     *
     * @param word Parola da valutare
     * @return <code>TRUE</code> se la parola inizia con un cancelletto,
     * <code>FALSE</code> altrimenti
     */
    private boolean isHashTag(String word) {
        return word.substring(0, 1).equals("#") ? true : false;
    }

    /**
     * Procedura di rimozione di simboli indesiderati dal testo. Si appoggia
     * alla funzione 'loadPunctuation()' per la definizione di quali simboli
     * eliminare.
     *
     * @param text Testo da ripulire
     * @return Testo ripulito dai simboli selezionati
     */
    private String removePunctuation(String text) {
        System.out.print("Punctuation ... ");
        for (String symbol : punctuation) {
            text = text.replace(symbol, " ");
        }
        System.out.print("REMOVED");
        return text;
    }

    /**
     * Procedura di elaborazione degli emoji presenti nel testo passato come
     * parametro. Durante la scansione vengono salvati e conteggiati nella
     * hashmap 'emoji', suddivisa a sua volta in una sottomappa per sentimento.
     *
     * @param text Il testo da analizzare
     * @param sentiment Il sentimento associato al testo analizzato
     * @return Il testo privo degli emoji riconosciuti
     */
    private String processEmoji(String text, String sentiment) {
        System.out.print("Emoji ... ");
        StrBuilder elaboratedText = new StrBuilder();
        try {
            BufferedReader br = new BufferedReader(new StringReader(text));
            String sCurrentLine;
            while ((sCurrentLine = br.readLine()) != null) {
                String[] splittedText = sCurrentLine.split(" ");
                for (String token : splittedText) {
                    if (EmojiManager.isEmoji(token)) {
                        elaborateEmoji(token, sentiment);
                    } else {
                        elaboratedText.append(" " + token);
                    }
                }
                elaboratedText.appendNewLine();
            }
        } catch (IOException ex) {
            Logger.getLogger(Analyser.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.out.print("PROCESSED");
        return elaboratedText.toString();
    }

    /**
     * Procedura di elaborazione del singolo emoji. Ad ogni occorrenza
     * riscontrata aumenta il conteggio nel relativo sentimento.
     *
     * @param word Codifica sotto forma di stringa dell'emoji
     * @param sentiment Sentimento del tweet dove è stato riscontrato
     */
    private void elaborateEmoji(String word, String sentiment) {
        word = EmojiParser.parseToAliases(word);
        //System.out.println(word);
        if (emoji.get(word).containsKey(sentiment)) {
            // se non è la prima volta che lo osservo in questo sentimento, aggiorno
            HashMap<String, Integer> old = emoji.get(word);
            old.replace(sentiment, old.get(sentiment) + 1);
            emoji.replace(word, old);
        } else {
            // se è la prima volta che lo osservo in questo sentimento, lo creo
            HashMap<String, Integer> temp = emoji.get(word);
            temp.put(sentiment, 1);
            emoji.put(word, temp);
        }
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

    private String processWord(String text, String sentiment) {
        StrBuilder elaboratedText = new StrBuilder();
        Properties props = new Properties();
        props.put("annotators", "tokenize, ssplit, pos, lemma");
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props, false);
        try {
            Class.forName(myDriver);
            Connection conn = DriverManager.getConnection(myUrl, myUser, myPass);
            BufferedReader br = new BufferedReader(new StringReader(text));
            String sCurrentLine;
            while ((sCurrentLine = br.readLine()) != null) {
                Annotation document = pipeline.process(sCurrentLine);
                for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
                    for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                        String lemma = token.get(CoreAnnotations.LemmaAnnotation.class);
                        if (lemma.length() > 2 && !StringUtils.isNumeric(lemma) && !lemma.contains("'")) {
                            if (isAlredyResLex(lemma, sentiment, conn)) {
                                countOldWord(lemma, sentiment);
                            } else {
                                countNewWord(lemma, sentiment);
                            }
                            elaboratedText.append(" " + lemma);
                        }
                    }
                    elaboratedText.appendNewLine();
                }
            }
            conn.close();
        } catch (IOException ex) {
            Logger.getLogger(Analyser.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(Analyser.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SQLException ex) {
            Logger.getLogger(Analyser.class.getName()).log(Level.SEVERE, null, ex);
        }
        return elaboratedText.toString();
    }

    private boolean isAlredyResLex(String lemma, String sentiment, Connection conn) {
        boolean answer = false;
        try {
            Statement st = conn.createStatement();
            String query = "SELECT * FROM " + sentiment + " WHERE WORD='" + lemma + "'";
            ResultSet rs = st.executeQuery(query);
            answer = rs.isBeforeFirst();
            rs.close();
            st.close();
        } catch (SQLException ex) {
            Logger.getLogger(Analyser.class.getName()).log(Level.SEVERE, null, ex);
        }
        return answer;
    }

    private void countOldWord(String lemma, String sentiment) {
        if (oldWords.containsKey(sentiment)) {
            if (oldWords.get(sentiment).containsKey(lemma)) {
                HashMap<String, Integer> old = oldWords.get(sentiment);
                old.replace(lemma, old.get(lemma) + 1);
                oldWords.replace(sentiment, old);
            } else {
                HashMap<String, Integer> temp = oldWords.get(sentiment);
                temp.put(lemma, 1);
                oldWords.put(sentiment, temp);
            }
        } else {
            HashMap<String, Integer> first = new HashMap<>();
            first.put(lemma, 1);
            oldWords.put(sentiment, first);
        }
    }

    private void countNewWord(String lemma, String sentiment) {
        if (newWords.containsKey(sentiment)) {
            if (newWords.get(sentiment).containsKey(lemma)) {
                HashMap<String, Integer> old = newWords.get(sentiment);
                old.replace(lemma, old.get(lemma) + 1);
                newWords.replace(sentiment, old);
            } else {
                HashMap<String, Integer> temp = newWords.get(sentiment);
                temp.put(lemma, 1);
                newWords.put(sentiment, temp);
            }
        } else {
            HashMap<String, Integer> first = new HashMap<>();
            first.put(lemma, 1);
            newWords.put(sentiment, first);
        }
    }

    private void storeResultsOperation() {
        try {
            Class.forName(myDriver);
            Connection conn = DriverManager.getConnection(myUrl, myUser, myPass);
            conn.setAutoCommit(false);

            //storeEmoticonsIntoDB(conn);
            //storeEmojiIntoDB(conn);
            storeHashtagsIntoDB(conn);
            //storeOldWordsIntoDB();
            //storeNewWordsIntoDB();

            conn.close();
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(Analyser.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SQLException ex) {
            Logger.getLogger(Analyser.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void storeEmoticonsIntoDB(Connection conn) {
        try {
            String queryCreate = "DROP TABLE EMOTICON";
            Statement stDel = conn.createStatement();
            stDel.executeQuery(queryCreate);
            conn.commit();
            stDel.close();
        } catch (SQLException ex) {
            System.out.println("La tabella EMOTICON non era presente precedentemente");
        }

        try {
            String queryCreate = "CREATE TABLE EMOTICON ( EMOTICON VARCHAR(50),";
            for (File sentiment : sentimentsFoldersList) {
                queryCreate += " " + sentiment.getName().toUpperCase() + " INT,";
            }
            queryCreate += " PRIMARY KEY(EMOTICON) )";
            Statement st = conn.createStatement();
            st.executeQuery(queryCreate);
            conn.commit();
            st.close();
        } catch (SQLException ex) {
            Logger.getLogger(Analyser.class.getName()).log(Level.SEVERE, null, ex);
        }

        String queryInsert = "INSERT INTO EMOTICON ("
                + "EMOTICON, "
                + "ANGER, "
                + "ANTICIPATION, "
                + "DISGUST, "
                + "FEAR, "
                + "JOY, "
                + "SADNESS, "
                + "SURPRISE, "
                + "TRUST) "
                + "VALUES "
                + "(?, ?, ?, ?, ?, ?, ?, ?, ?)";
        // query modificabile per non esplicitare i sentimenti ma per il momento va bene così

        PreparedStatement pstmt;
        try {
            pstmt = conn.prepareStatement(queryInsert);
            for (Map.Entry emo : emoticons.entrySet()) {
                int i = 1;
                String id = (String) emo.getKey();
                HashMap<String, Integer> sentimentsHash = (HashMap<String, Integer>) emo.getValue();
                id = id.replace("'", "''");
                pstmt.setString(i++, id);

                for (File sentiment : sentimentsFoldersList) {
                    if (sentimentsHash.containsKey(sentiment.getName())) {
                        pstmt.setInt(i++, sentimentsHash.get(sentiment.getName()));
                    } else {
                        pstmt.setInt(i++, 0);
                    }
                }
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            conn.commit();
            pstmt.close();
        } catch (SQLException ex) {
            Logger.getLogger(Analyser.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void storeEmojiIntoDB(Connection conn) {
        try {
            String queryCreate = "DROP TABLE EMOJI";
            Statement stDel = conn.createStatement();
            stDel.executeQuery(queryCreate);
            conn.commit();
            stDel.close();
        } catch (SQLException ex) {
            System.out.println("La tabella EMOJI non era presente precedentemente");
        }

        try {
            String queryCreate = "CREATE TABLE EMOJI ( ALIAS VARCHAR(50), HTML VARCHAR(20),";
            for (File sentiment : sentimentsFoldersList) {
                queryCreate += " " + sentiment.getName().toUpperCase() + " INT,";
            }
            queryCreate += " PRIMARY KEY(ALIAS) )";
            Statement st = conn.createStatement();
            st.executeQuery(queryCreate);
            conn.commit();
            st.close();
        } catch (SQLException ex) {
            Logger.getLogger(Analyser.class.getName()).log(Level.SEVERE, null, ex);
        }

        String queryInsert = "INSERT INTO EMOJI ("
                + "ALIAS, HTML, "
                + "ANGER, "
                + "ANTICIPATION, "
                + "DISGUST, "
                + "FEAR, "
                + "JOY, "
                + "SADNESS, "
                + "SURPRISE, "
                + "TRUST) "
                + "VALUES "
                + "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        // query modificabile per non esplicitare i sentimenti ma per il momento va bene così

        PreparedStatement pstmt;
        try {
            pstmt = conn.prepareStatement(queryInsert);
            for (Map.Entry emo : emoji.entrySet()) {
                int i = 1;
                String alias = (String) emo.getKey();
                HashMap<String, Integer> sentimentsHash = (HashMap<String, Integer>) emo.getValue();
                pstmt.setString(i++, alias);
                String html = EmojiManager.getForAlias(alias).getHtml();
                pstmt.setString(i++, html);
                for (File sentiment : sentimentsFoldersList) {
                    if (sentimentsHash.containsKey(sentiment.getName())) {
                        pstmt.setInt(i++, sentimentsHash.get(sentiment.getName()));
                    } else {
                        pstmt.setInt(i++, 0);
                    }
                }
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            conn.commit();
            pstmt.close();
        } catch (SQLException ex) {
            Logger.getLogger(Analyser.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void storeHashtagsIntoDB(Connection conn) {
        try {
            String queryCreate = "DROP TABLE HASHTAG";
            Statement stDel = conn.createStatement();
            stDel.executeQuery(queryCreate);
            conn.commit();
            stDel.close();
        } catch (SQLException ex) {
            System.out.println("La tabella HASHTAG non era presente precedentemente");
        }

        try {
            String queryCreate = "CREATE TABLE HASHTAG ( HASHTAG VARCHAR(50),";
            for (File sentiment : sentimentsFoldersList) {
                queryCreate += " " + sentiment.getName().toUpperCase() + " INT,";
            }
            queryCreate += " PRIMARY KEY(HASHTAG) )";
            Statement st = conn.createStatement();
            st.executeQuery(queryCreate);
            conn.commit();
            st.close();
        } catch (SQLException ex) {
            Logger.getLogger(Analyser.class.getName()).log(Level.SEVERE, null, ex);
        }

        String queryInsert = "INSERT INTO HASHTAG ("
                + "HASHTAG, "
                + "ANGER, "
                + "ANTICIPATION, "
                + "DISGUST, "
                + "FEAR, "
                + "JOY, "
                + "SADNESS, "
                + "SURPRISE, "
                + "TRUST) "
                + "VALUES "
                + "(?, ?, ?, ?, ?, ?, ?, ?, ?)";
        // query modificabile per non esplicitare i sentimenti ma per il momento va bene così

        PreparedStatement pstmt;
        try {
            pstmt = conn.prepareStatement(queryInsert);
            for (Map.Entry tag : hashtags.entrySet()) {
                int i = 1;
                String id = (String) tag.getKey();
                HashMap<String, Integer> sentimentsHash = (HashMap<String, Integer>) tag.getValue();
                pstmt.setString(i++, id);
                System.out.println("----"+ id);
                for (File sentiment : sentimentsFoldersList) {
                    if (sentimentsHash.containsKey(sentiment.getName())) {
                        pstmt.setInt(i++, sentimentsHash.get(sentiment.getName()));
                    } else {
                        pstmt.setInt(i++, 0);
                    }
                }
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            conn.commit();
            pstmt.close();
        } catch (SQLException ex) {
            Logger.getLogger(Analyser.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
