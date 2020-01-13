/**
 * 
 */
package org.bgu.ise.ddb.items;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import javax.servlet.http.HttpServletResponse;

import org.bgu.ise.ddb.MediaItems;
import org.bgu.ise.ddb.ParentController;
import org.bgu.ise.ddb.User;
import org.bson.Document;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;

import javafx.util.Pair;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.eq;
import static java.lang.Integer.parseInt;
import static oracle.jdbc.OracleTypes.NUMBER;
import static oracle.jdbc.OracleTypes.STRUCT;



/**
 * @author Alex
 *
 */
@RestController
@RequestMapping(value = "/items")
public class ItemsController extends ParentController {
	private Connection oracleConnection;
	private MongoClient mongoConnection;
	private MongoDatabase database;
	private MongoCollection<Document> coll;

	private void openOracleConnection() {
		try {
            Class.forName("oracle.jdbc.driver.OracleDriver");
            this.oracleConnection = DriverManager.getConnection("jdbc:oracle:thin:@132.72.65.216:1521/ORACLE", "saarzeev", "abcd");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
	}
	
	private void openMongoConnection() {
		try {
			mongoConnection = new MongoClient("127.0.0.1", 27017);
			database = mongoConnection.getDatabase("dbProject");
			coll = database.getCollection("Mediaitems");
			System.out.println("Connection Established");
		} catch (Exception e) {
			e.printStackTrace();
			mongoConnection.close();
		}
	}
	
	/**
	 * The function copy all the items(title and production year) from the Oracle table MediaItems to the System storage.
	 * The Oracle table and data should be used from the previous assignment
	 */
	@RequestMapping(value = "fill_media_items", method={RequestMethod.GET})
	public void fillMediaItems(HttpServletResponse response){

		openOracleConnection();
		openMongoConnection();
		
        ResultSet rs = null;
        PreparedStatement ps = null;
        try {
            ps = this.oracleConnection.prepareStatement("SELECT TITLE, PROD_YEAR  FROM MEDIAITEMS");
            rs = ps.executeQuery();
            while ( rs.next() ) {
            	String title = rs.getString("TITLE");
            	int prodYear = rs.getInt("PROD_YEAR");
            	if(!isItemExists(title)) {
            		Document newMediaItem = new Document("title", title).append("prod_year", prodYear);
         			coll.insertOne(newMediaItem);
            	}
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }finally {
            try {
                rs.close();
                ps.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        
        mongoConnection.close();
        try {
			oracleConnection.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		HttpStatus status = HttpStatus.OK;
		response.setStatus(status.value());
	} 
	
	

	/**
	 * The function copy all the items from the remote file,
	 * the remote file have the same structure as the films file from the previous assignment.
	 * You can assume that the address protocol is http
	 * @throws IOException 
	 */
	@RequestMapping(value = "fill_media_items_from_url", method={RequestMethod.GET})
	public void fillMediaItemsFromUrl(@RequestParam("url")    String urladdress,
			HttpServletResponse response) throws IOException{

		URL url = new URL(urladdress);
		BufferedReader br = null;
		String line = "";

		try {
			br = new BufferedReader(new BufferedReader(new InputStreamReader(url.openStream())));
			while ((line = br.readLine()) != null) {
				
				String[] tuple = line.split(",");
				String title= tuple[0];
				String prodYearStr=tuple[1];
				
				try {
					int prodYear = Integer.parseInt(prodYearStr);
					if(!isItemExists(title))
					{
						openMongoConnection();
						
						Document newMediaItem = new Document("title", title).append("prod_year", prodYear);
		     			coll.insertOne(newMediaItem);
						
						mongoConnection.close();
					}
					
				} catch (Exception e) {
					e.printStackTrace();;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();;
		}

		HttpStatus status = HttpStatus.OK;
		response.setStatus(status.value());
	}
	
	
	private boolean isItemExists(String title) {
		openMongoConnection();
		
		Document myDoc = coll.find(eq("title", title)).first();
		
		mongoConnection.close();
		
		return myDoc != null;
	}

	/**
	 * The function retrieves from the system storage N items,
	 * order is not important( any N items) 
	 * @param topN - how many items to retrieve
	 * @return
	 */
	@RequestMapping(value = "get_topn_items",headers="Accept=*/*", method={RequestMethod.GET},produces="application/json")
	@ResponseBody
	@org.codehaus.jackson.map.annotate.JsonView(MediaItems.class)
	public  MediaItems[] getTopNItems(@RequestParam("topn")    int topN){

		openMongoConnection();
		
		List<MediaItems> mediaItems = new ArrayList<MediaItems>();
		
		MongoCursor<Document> cursor = coll.find().limit(topN).iterator();
		try {           
		    while (cursor.hasNext()) {
		    	Document mediaItemDoc = cursor.next();

				String title = mediaItemDoc.getString("title");
				Integer prodYear = mediaItemDoc.getInteger("prod_year");
				
				mediaItems.add(new MediaItems(title, prodYear.intValue()));   
		    }
		} finally {
		    cursor.close();
		}
		
		mongoConnection.close();

		return mediaItems.toArray(new MediaItems[mediaItems.size()]);
	}
		

}
