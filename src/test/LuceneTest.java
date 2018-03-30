package test;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.bdrc.lucene.zh.ChineseAnalyzer;

public class LuceneTest {
    
    @Rule
    public TemporaryFolder folder= new TemporaryFolder();
    
    // "丹珠尔"
    // Analyzer analyzer = new ChineseAnalyzer("TC2PYstrict", false, 0);
    @Test
    public void testTC2PYstrict() throws IOException, ParseException {
        String profile = "TC2PYstrict";
        File testSubFolder = folder.newFolder(profile);
        
        Analyzer indexingAnalyzer = new ChineseAnalyzer(profile, false, 0);
        Analyzer queryingAnalyzer = new StandardAnalyzer();
        
        String input = "丹珠尔";
        String query = "";
        indexTest(input, indexingAnalyzer, testSubFolder);
        
        searchIndex(query, queryingAnalyzer, testSubFolder, 1);
        
        folder.delete();  // just to be sure it is done
    }
    
    void searchIndex(String queryString, Analyzer analyzer, File indexFolder, int repeat) throws IOException, ParseException {
        String field = "contents";
        String queries = null;
        
        IndexReader reader = DirectoryReader.open(FSDirectory.open(indexFolder.toPath()));
        IndexSearcher searcher = new IndexSearcher(reader);
        QueryParser parser = new QueryParser(field, analyzer);
        Query query = parser.parse(queryString);
     // Collect enough docs to show 5 pages
        TopDocs results = searcher.search(query, 5 * hitsPerPage);
        ScoreDoc[] hits = results.scoreDocs;
        
        int numTotalHits = results.totalHits;
        System.out.println(numTotalHits + " total matching documents");

        if (repeat > 0) {                           // repeat & time as benchmark
            Date start = new Date();
            for (int i = 0; i < repeat; i++) {
                searcher.search(query, 100);
            }
            Date end = new Date();
            System.out.println("Time: "+(end.getTime()-start.getTime())+"ms");
        }
        reader.close();
    }
    
    /** Bootstrapping for indexDoc() */
    void indexTest(String input, Analyzer analyzer, File testSubFolder) throws IOException {
        // create temp file and write input string in it.
        File testFile = File.createTempFile("test-content_", ".txt", testSubFolder);
        BufferedWriter bw = new BufferedWriter(new FileWriter(testFile));
        bw.write(input);
        bw.close();
        
        // config for indexDoc()
        final Path docPath = Paths.get(testFile.getAbsolutePath());
        Directory dir = FSDirectory.open(Paths.get(testSubFolder.getAbsolutePath()));

        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
        IndexWriter writer = new IndexWriter(dir, iwc);
        indexDoc(writer, docPath, Files.getLastModifiedTime(docPath).toMillis());
    }
    
    /** Indexes a single document */
    static void indexDoc(IndexWriter writer, Path file, long lastModified) throws IOException {
      try (InputStream stream = Files.newInputStream(file)) {
        // make a new, empty document
        Document doc = new Document();
        
        // path field in the index
        Field pathField = new StringField("path", file.toString(), Field.Store.YES);
        doc.add(pathField);
        
        // modified field in index (last modified date of file)
        doc.add(new LongPoint("modified", lastModified));
         
        // file content is tokenized and indexed, but not stored. (UTF-8 expected)
        doc.add(new TextField("contents", new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))));
        
        // New index, so we just add the document (no old document can be there):
        writer.addDocument(doc); 
        writer.close();
      }
    }
}
