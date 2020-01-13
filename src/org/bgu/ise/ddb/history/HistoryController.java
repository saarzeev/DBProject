/**
 * 
 */
package org.bgu.ise.ddb.history;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.bgu.ise.ddb.ParentController;
import org.bgu.ise.ddb.User;
import org.bson.Document;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.operation.OrderBy;

/**
 * @author Alex
 *
 */
@RestController
@RequestMapping(value = "/history")
public class HistoryController extends ParentController{
	private MongoClient mongoConnection;
	private MongoDatabase database;
	private MongoCollection<Document> coll;
	
	private void openConnection() {
		try {
			mongoConnection = new MongoClient("127.0.0.1", 27017);
			database = mongoConnection.getDatabase("dbProject");
			coll = database.getCollection("History");
			System.out.println("Connection Established");
		} catch (Exception e) {
			e.printStackTrace();
			mongoConnection.close();
		}

	}
	
	/**
	 * The function inserts to the system storage triple(s)(username, title, timestamp). 
	 * The timestamp - in ms since 1970
	 * Advice: better to insert the history into two structures( tables) in order to extract it fast one with the key - username, another with the key - title
	 * @param username
	 * @param title
	 * @param response
	 */
	@RequestMapping(value = "insert_to_history", method={RequestMethod.GET})
	public void insertToHistory (@RequestParam("username")    String username,
			@RequestParam("title")   String title,
			HttpServletResponse response){
		
		openConnection();
		
		System.out.println(username+" "+title);
		Document newHistory = new Document("username", username)
									.append("title", title)
									.append("viewtime", System.currentTimeMillis());

		coll.insertOne(newHistory);
		
		mongoConnection.close();
		HttpStatus status = HttpStatus.OK;
		response.setStatus(status.value());
	}
	
	
	
	/**
	 * The function retrieves  users' history
	 * The function return array of pairs <title,viewtime> sorted by VIEWTIME in descending order
	 * @param username
	 * @return
	 */
	@RequestMapping(value = "get_history_by_users",headers="Accept=*/*", method={RequestMethod.GET},produces="application/json")
	@ResponseBody
	@org.codehaus.jackson.map.annotate.JsonView(HistoryPair.class)
	public  HistoryPair[] getHistoryByUser(@RequestParam("entity")    String username){
				
		String wantedCredential = "title";
		String searchByField = "username";
		
		return findInHistoryByField(username, wantedCredential, searchByField);
	}

	private HistoryPair[] findInHistoryByField(String fieldValue, String wantedCredential, String searchByField) {
		
		openConnection();
		MongoCursor<Document> cursor = coll.find(eq(searchByField, fieldValue)).sort(new BasicDBObject("viewtime", OrderBy.DESC)).iterator();		
		
		List<HistoryPair> historyPair = new ArrayList<HistoryPair>();
		
		try {           
		    while (cursor.hasNext()) {
		    	Document historyDoc = cursor.next();
		    	
		    	String credential = historyDoc.getString(wantedCredential);
		    	Date viewtime = new Date(historyDoc.getLong("viewtime"));
		    	
		    	historyPair.add(new HistoryPair(credential, viewtime));
		    }
		} finally {
		    cursor.close();
		}   
		
		mongoConnection.close();
		
		return historyPair.toArray(new HistoryPair[historyPair.size()]);
	}
	
	
	/**
	 * The function retrieves  items' history
	 * The function return array of pairs <username,viewtime> sorted by VIEWTIME in descending order
	 * @param title
	 * @return
	 */
	@RequestMapping(value = "get_history_by_items",headers="Accept=*/*", method={RequestMethod.GET},produces="application/json")
	@ResponseBody
	@org.codehaus.jackson.map.annotate.JsonView(HistoryPair.class)
	public  HistoryPair[] getHistoryByItems(@RequestParam("entity")    String title){
		
		String wantedCredential = "username";
		String searchByField = "title";
		
		return findInHistoryByField(title, wantedCredential, searchByField);
	}
	
	/**
	 * The function retrieves all the  users that have viewed the given item
	 * @param title
	 * @return
	 */
	@RequestMapping(value = "get_users_by_item",headers="Accept=*/*", method={RequestMethod.GET},produces="application/json")
	@ResponseBody
	@org.codehaus.jackson.map.annotate.JsonView(HistoryPair.class)
	public  User[] getUsersByItem(@RequestParam("title") String title){
		HistoryPair[] pairs = getHistoryByItems(title);
		List<User> users = new ArrayList<User>();
		openConnection();
		this.coll = database.getCollection("Users");
		
		for(HistoryPair h : pairs) {
			String username = h.getCredentials();
			
			Document userDoc = this.coll.find(eq("username", username)).first();

			//String password = userDoc.getString("password");
			String firstName = userDoc.getString("firstname");
			String lastName = userDoc.getString("lastname");
			
			users.add(new User(username/*, password*/, firstName, lastName));    
		}
		
		return users.toArray(new User[users.size()]);
	}
	
	/**
	 * The function calculates the similarity score using Jaccard similarity function:
	 *  sim(i,j) = |U(i) intersection U(j)|/|U(i) union U(j)|,
	 *  where U(i) is the set of usernames which exist in the history of the item i.
	 * @param title1
	 * @param title2
	 * @return
	 */
	@RequestMapping(value = "get_items_similarity",headers="Accept=*/*", method={RequestMethod.GET},produces="application/json")
	@ResponseBody
	public double  getItemsSimilarity(@RequestParam("title1") String title1,
			@RequestParam("title2") String title2){
		
		Set<String> users1 = new HashSet<String>();
		Set<String> users2 = new HashSet<String>();
		
		HistoryPair[] pairs1 = getHistoryByItems(title1);
		HistoryPair[] pairs2 = getHistoryByItems(title2);
		
		for(HistoryPair h : pairs1) {
			users1.add(h.getCredentials());
		}
		for(HistoryPair h : pairs2) {
			users2.add(h.getCredentials());
		}
		
		Set<String> intersection = new HashSet<String>(users1);
		intersection.retainAll(users2);
		
		Set<String> union = new HashSet<String>(users1);
		union.addAll(users2);
		
		//sim(i,j) = |U(i) intersection U(j)|/|U(i) union U(j)|
		double ret = union.size() > 0 ? intersection.size()/(double)union.size() : Double.MAX_VALUE;
		return ret;
	}
}
