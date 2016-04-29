/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package logic;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author TTm
 */
public class Loader extends HttpServlet {

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        File dir = new File("C:/Dropbox/lex_res");
        File[] sentimentsFoldersList = dir.listFiles();
        System.out.println(sentimentsFoldersList.length);

        for (File sentimentFolder : sentimentsFoldersList) {
            System.out.println("------ FOLDER " + sentimentFolder.getName() + "------");
            elaborateSentiment(sentimentFolder);
        }

        // Usa un BufferReader per wrappare un FileReader e leggerne il contenuto
        // prima di poterlo usare occorre valutare come muoversi dentro la directory
//        BufferedReader br = null;
//        try {
//            String sCurrentLine;
//            br = new BufferedReader(new FileReader("C:\\testing.txt"));
//            while ((sCurrentLine = br.readLine()) != null) {
//                System.out.println(sCurrentLine);
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        } finally {
//            try {
//                if (br != null) {
//                    br.close();
//                }
//            } catch (IOException ex) {
//                ex.printStackTrace();
//            }
//        }
        // Solito pattern per presentare il contenuto su pagina, andrebbe 
        // spostato per mantenere MVC e un minimo di pulizia/ordine
//        response.setContentType("text/html;charset=UTF-8");
//        try (PrintWriter out = response.getWriter()) {
//            /* TODO output your page here. You may use following sample code. */
//            out.println("<!DOCTYPE html>");
//            out.println("<html>");
//            out.println("<head>");
//            out.println("<title>Servlet Loader</title>");
//            out.println("</head>");
//            out.println("<body>");
//            out.println("<h1>Servlet Loader at " + request.getContextPath() + "</h1>");
//            out.println("</body>");
//            out.println("</html>");
//        }
    }

    /**
     * Metodo per l'elaborazione della singola cartella, quindi del singolo
     * sentimento. Analizza i singoli file contenuti nella directory,
     * caricandoli le parole associate al sentimento nella relativa tabella nel
     * database.
     *
     * @param sentiment Cartella contenente i vari file (risorse) associati ad
     * un'emozione
     */
    public void elaborateSentiment(File sentiment) {
        File[] sentimentResList = sentiment.listFiles();
        int numRes = sentimentResList.length;
        
        // HashMap temporanea per ogni sentimento che controllerà la presenza di una 
        // parola in più risorse e ne terrà il conteggio
        HashMap<String, Integer> hash = new HashMap<String, Integer>();

        BufferedReader br = null;
        // mi trovo dentro la cartella di un sentimento, ciclo per ogni risorsa
        for (File sentRes : sentimentResList) {
            System.out.println("###### Open res: " + sentRes.getName() + " ######");
            try {
                String sCurrentLine;
                br = new BufferedReader(new FileReader(sentRes.getAbsolutePath()));
                // finchè ci sono parole nella risorsa aperta da scorrere
                while ((sCurrentLine = br.readLine()) != null) {
                    if (hash.get(sCurrentLine) != null) {
                        // se la parola è già stata inserita
                        hash.put(sCurrentLine, hash.get(sCurrentLine) + 1);
                        System.out.println("REPEATED: " + sCurrentLine);
                    } else {
                        // se la parola non è ancora stata inserita
                        hash.put(sCurrentLine, 1);
                        System.out.println("New: " + sCurrentLine);
                    }

                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (br != null) {
                        br.close();
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            System.out.println("###### Close res: " + sentRes.getName() + " ######");
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
