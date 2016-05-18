package logic;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
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

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // TO-DO caricate tutte in memoria tutte le varie risorse strane da dover trattare
        // punteggiatura, emoji, slang
        loadEmoticons();
        loadSlangs();
        loadStopwords();
        loadPunctuation();

        // Per ogni cartella fa partire l'elaborazione di un 'sentimento'
        for (File sentimentFolder : sentimentsFoldersList) {
            System.out.println("------ SENTIMENT " + sentimentFolder.getName() + "------");
            elaborateSentiment(sentimentFolder);
        }

    }

    private void elaborateSentiment(File sentimentFolder) {
        File[] sentimentTweetList = sentimentFolder.listFiles();
        // mi trovo dentro la cartella di un sentimento, ciclo per ogni raccolta di tweet
        for (File tweetFile : sentimentTweetList) {
            System.out.println("###### Open res: " + tweetFile.getName() + " ######");
            try {
                Path path = Paths.get(tweetFile.getAbsolutePath());
                Path destination = Paths.get(sentimentFolder + File.separator + sentimentFolder.getName() + ".txt");

                Charset charset = StandardCharsets.UTF_8;
                byte[] contentByte = Files.readAllBytes(path);
                String contentString = new String(contentByte, charset);

                contentString = removeTwitterWords(contentString);
                contentString = processEmoticons(contentString, sentimentFolder.getName());
                contentString = processSlangWords(contentString);
                contentString = processStopwords(contentString);
                contentString = processHashtags(contentString, sentimentFolder.getName());
                contentString = removePunctuation(contentString);

                //contentString = innerElaboration(contentString, sentimentFolder.getName());
                Files.write(destination, contentString.getBytes(charset));

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
                //System.out.println("DEBUG - " + sCurrentLine);

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
                //System.out.println("DEBUG - " + parts[0] + " : " + parts[1]);

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
                System.out.println(sCurrentLine);
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
                stopwords.add(" " + sCurrentLine + " ");

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
     * Analizza il testo passato come input e ne rimuove le parole chiave
     * tipiche di un tweet 'USERNAME' e 'URL'.
     *
     * @param text Testo da analizzare
     * @return Testo ripulito delle parole 'USERNAME' e 'URL'
     */
    private String removeTwitterWords(String text) {
        text = text.replace("USERNAME", "");
        text = text.replace("URL", "");
        System.out.println("TwitterWords REMOVED");
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
        for (Map.Entry emoticon : emoticons.entrySet()) {
            Integer tempCont = StringUtils.countMatches(text, (String) emoticon.getKey());
            HashMap<String, Integer> tempHash = emoticons.get((String) emoticon.getKey());
            tempHash.put(sentiment, tempCont);
            emoticons.put((String) emoticon.getKey(), tempHash);
        }
        for (Map.Entry emoticon : emoticons.entrySet()) {
            text = text.replace((String) emoticon.getKey(), "");
        }
        System.out.println("-" + sentiment + "- Emoticons PROCESSED");
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
        for (Map.Entry temp : slangs.entrySet()) {
            String slangForm = (String) temp.getKey();
            String extendedForm = (String) temp.getValue();
            text = text.replace(slangForm, extendedForm);
        }
        System.out.println("Slang PROCESSED");
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
        for (String temp : stopwords) {
            text = text.replace(temp, " ");
        }
        System.out.println("Stopword REMOVED");
        return text;
    }

    /**
     * Procedura di elaborazione degi hashtag presenti nel testo passato come parametro.
     * Durante la scansione vengono salvati e conteggiati nella hashmap 'hashtags',
     * suddivisa a sua volta in una sottomappa per sentimento. 
     * @param text Il testo da analizzare
     * @param sentiment Il sentimento associato al testo analizzato 
     * @return Il testo privo degli hashtag
     */
    private String processHashtags(String text, String sentiment) {
        StrBuilder elaboratedText = new StrBuilder();
        try {
            BufferedReader br = new BufferedReader(new StringReader(text));
            String sCurrentLine;
            while ((sCurrentLine = br.readLine()) != null) {
                String[] splittedText = sCurrentLine.split(" ");
                for (String token : splittedText) {
                    if (token.length() > 1 && isHashTag(token)) {
                        //System.out.println(token + " - " + tempCont);
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
        return elaboratedText.toString();
    }

    /**
     * Controlla se la parola potrebbe essere un hashtag, valutando il primo carattere.
     * @param word Parola da valutare
     * @return <code>TRUE</code> se la parola inizia con un cancelletto, <code>FALSE</code> altrimenti
     */
    private boolean isHashTag(String word) {
        return word.substring(0, 1).equals("#") ? true : false;
    }

    /**
     * Procedura di salvataggio e conteggio delle occorrenze degli hashtag. Viene
     * invocato quando nella procedura di analisi degli hashtag, si riscontra un'occorrenza: 
     * a questo punto se è già presente si aggiorna il conteggio, altrimenti si inizializza.
     * Il conteggio viene fatto considerando in quale 'sentimento' viene trovato l'hashtag.
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
        System.out.println("Hashtag PROCESSED");
    }

    /**
     * Procedura di rimozione di simboli indesiderati dal testo. Si appoggia alla 
     * funzione 'loadPunctuation()' per la definizione di quali simboli eliminare.
     * @param text Testo da ripulire
     * @return Testo ripulito dai simboli selezionati
     */
    private String removePunctuation(String text) {
        for (String symbol : punctuation) {
            text = text.replace(symbol, " ");
        }
        System.out.println("Punctuation REMOVED");
        return text;
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

}
