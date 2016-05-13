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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.nio.file.Path;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author TTm
 */
public class Analyser extends HttpServlet {

    File dirTweet = new File("C:/Dropbox/tweet_temp");
    File[] sentimentsFoldersList = dirTweet.listFiles();

    int sentimentIndex = 0;

    // HashMap dove verranno caricate le varie emoticon. Per ogniuna di queste è presente una hash
    // interna che conterrà il conteggio delle occorrenze associato ad ogni sentimento
    HashMap<String, HashMap<String, Integer>> emoticons = new HashMap<>();

    // HashMap contenente gli slang che andranno ricercati nei tweet e sostituiti con la
    // loro forma estesa
    HashMap<String, String> slang = new HashMap<>();

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // TO-DO caricate tutte in memoria tutte le varie risorse strane da dover trattare
        // punteggiatura, emoji, slang
        //loadEmoticons();
        //loadSlang();

        // Per ogni cartella fa partire l'elaborazione di un 'sentimento'
        for (File sentimentFolder : sentimentsFoldersList) {
            System.out.println("------ SENTIMENT " + sentimentFolder.getName() + "------");
            elaborateSentiment(sentimentFolder);
        }

    }

    private void elaborateSentiment(File sentimentFolder) {
        // 1) check emoji --> usare Hash<String, Int[]> globale, ad ogni 
        // corrisponderà un indice 
        // 2) check slang con sostituzione -- > caricare in una 
        // Hash<String,String> tutto quello che è sostituibile
        // 3) check eliminazione di ogni punteggiatura --> hash da scorrere con replace
        // 4) eliminazione di USERNAME e URL --> idem come sopra
        // 5) eliminazione di ogni stop word --> usare libreria di standord? eliminare e basta?
        // 
        File[] sentimentTweetList = sentimentFolder.listFiles();
        // mi trovo dentro la cartella di un sentimento, ciclo per ogni raccolta di tweet
        for (File tweetFile : sentimentTweetList) {
            try {
                Path path = Paths.get(tweetFile.getAbsolutePath());
                Charset charset = StandardCharsets.UTF_8;
                byte[] contentByte = Files.readAllBytes(path);
                String contentString = new String(contentByte,charset);
                
                //content = content.replaceAll("USERNAME", "");
                Files.write(path, contentString.getBytes(charset));
            } catch (IOException ex) {
                Logger.getLogger(Analyser.class.getName()).log(Level.SEVERE, null, ex);
            }
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
            File emoticonsFile = new File("C:/Dropbox/lex_util/emoticon.txt");
            BufferedReader br = new BufferedReader(new FileReader(emoticonsFile.getAbsolutePath()));
            String sCurrentLine;
            // Analizza ogni riga del file l'aggiunge alla hash globale
            while ((sCurrentLine = br.readLine()) != null) {
                emoticons.put(sCurrentLine, new HashMap<String, Integer>());
                //System.out.println("DEBUG - " + sCurrentLine);
            }

        } catch (FileNotFoundException ex) {
            Logger.getLogger(Analyser.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Analyser.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Precedura di precaricamento degli slang per la successiva sostituzione
     * all'interno dei tweet.
     */
    private void loadSlang() {
        try {
            // Punta al file contenente l'elenco degli slang/abbreviazioni
            File slangFile = new File("C:/Dropbox/lex_util/slang.txt");
            BufferedReader br = new BufferedReader(new FileReader(slangFile.getAbsolutePath()));
            String sCurrentLine;
            // Analizza ogni riga del file l'aggiunge alla hash globale
            while ((sCurrentLine = br.readLine()) != null) {
                String[] parts = sCurrentLine.split(":");
                slang.put(parts[0], parts[1]);
                //System.out.println("DEBUG - " + parts[0] + " : " + parts[1]);

            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Analyser.class
                    .getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Analyser.class
                    .getName()).log(Level.SEVERE, null, ex);
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
}


// Trattamento stop-word
//   Properties props = new Properties();
//   props.put("annotators", "tokenize, ssplit, stopword");
//   props.setProperty("customAnnotatorClass.stopword", "intoxicant.analytics.coreNlp.StopwordAnnotator");
//
//   StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
//   Annotation document = new Annotation(example);
//   pipeline.annotate(document);
//   List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);
