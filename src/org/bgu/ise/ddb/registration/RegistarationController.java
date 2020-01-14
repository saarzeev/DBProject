/**
 * 
 */
package org.bgu.ise.ddb.registration;

import java.io.IOException;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import javax.servlet.http.HttpServletResponse;
import java.sql.Timestamp;
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
import com.mongodb.MongoClientURI;
import com.mongodb.ServerAddress;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;

import org.bson.Document;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;

import com.mongodb.Block;

import com.mongodb.client.MongoCursor;
import static com.mongodb.client.model.Filters.*;
import com.mongodb.client.result.DeleteResult;
import static com.mongodb.client.model.Updates.*;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alex
 *
 */
@RestController
@RequestMapping(value = "/registration")
public class RegistarationController extends ParentController {
	private MongoClient mongoConnection;
	private MongoDatabase database;
	private MongoCollection<Document> coll;

	private void openConnection() {
		try {
			MongoClientOptions settings = MongoClientOptions.builder().codecRegistry(com.mongodb.MongoClient.getDefaultCodecRegistry()).build();
			mongoConnection = new MongoClient(new ServerAddress("localhost", 27017), settings);
			database = mongoConnection.getDatabase("dbProject");
			coll = database.getCollection("Users");
			System.out.println("Connection Established");
		} catch (Exception e) {
			e.printStackTrace();
			mongoConnection.close();
		}

	}

	/**
	 * The function checks if the username exist, in case of positive answer
	 * HttpStatus in HttpServletResponse should be set to HttpStatus.CONFLICT, else
	 * insert the user to the system and set to HttpStatus in HttpServletResponse
	 * HttpStatus.OK
	 * 
	 * @param username
	 * @param password
	 * @param firstName
	 * @param lastName
	 * @param response
	 */
	@RequestMapping(value = "register_new_customer", method = { RequestMethod.POST })
	public void registerNewUser(@RequestParam("username") String username, @RequestParam("password") String password,
			@RequestParam("firstName") String firstName, @RequestParam("lastName") String lastName,
			HttpServletResponse response) {

		openConnection();

		System.out.println(username + " " + password + " " + lastName + " " + firstName);
		HttpStatus status;

		if (!isUserExists(username)) {
			Document newUser = new Document("username", username).append("password", password)
					.append("firstname", firstName).append("lastname", lastName)
					.append("registrationTimestamp", System.currentTimeMillis());

			coll.insertOne(newUser);

			status = HttpStatus.OK;
		} else {
			status = HttpStatus.CONFLICT;
		}
		mongoConnection.close();
		response.setStatus(status.value());
	}

	private boolean isUserExists(String username) {
		Document myDoc = coll.find(eq("username", username)).first();
		return myDoc != null;
	}

	/**
	 * The function returns true if the received username exist in the system
	 * otherwise false
	 * 
	 * @param username
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "is_exist_user", method = { RequestMethod.GET })
	public boolean isExistUser(@RequestParam("username") String username) throws IOException {
		openConnection();

		System.out.println(username);
		boolean result = isUserExists(username);

		mongoConnection.close();
		return result;

	}

	/**
	 * The function returns true if the received username and password match a
	 * system storage entry, otherwise false
	 * 
	 * @param username
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "validate_user", method = { RequestMethod.POST })
	public boolean validateUser(@RequestParam("username") String username, @RequestParam("password") String password)
			throws IOException {

		boolean res = false;
		openConnection();

		System.out.println(username + " " + password);
		Document myDoc = coll.find(and(eq("username", username), (eq("password", password)))).first();

		res = (myDoc != null);
		mongoConnection.close();
		
		System.out.println(getAllUsers());
		return res;

	}

	/**
	 * The function retrieves number of the registered users in the past n days
	 * 
	 * @param days
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "get_number_of_registred_users", method = { RequestMethod.GET })
	public int getNumberOfRegistredUsers(@RequestParam("days") int days) throws IOException {
		
		openConnection();
		
		System.out.println(days + "");
		int result = 0;
		long earlisetTimeAccepted = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000);
		Iterator iterator;

		iterator = coll.find(gte("registrationTimestamp", earlisetTimeAccepted)).iterator();
		while (iterator.hasNext()) {
			iterator.next();
			result++;
		}

		mongoConnection.close();
		return result;
	}

	/**
	 * The function retrieves all the users
	 * 
	 * @return
	 */
	@RequestMapping(value = "get_all_users", headers = "Accept=*/*", method = {
			RequestMethod.GET }, produces = "application/json")
	@ResponseBody
	@org.codehaus.jackson.map.annotate.JsonView(User.class)
	public User[] getAllUsers() {
//		 new Document("username", username).append("password", password)
//			.append("firstname", firstName).append("lastname", lastName)
//			.append("registrationTimestamp", System.currentTimeMillis());
		openConnection();
		 List<User> users = new ArrayList<User>();
		// :TODO your implementation
		MongoCursor<Document> cursor = coll.find().iterator();

		try {           
		    while (cursor.hasNext()) {
		    	Document userDoc = cursor.next();

				String username = userDoc.getString("username");
				String password = userDoc.getString("password");
				String firstName = userDoc.getString("firstname");
				String lastName = userDoc.getString("lastname");
				
				users.add(new User(username, password, firstName, lastName));   
		    }
		} finally {
		    cursor.close();
		}   
		
		mongoConnection.close();

		return users.toArray(new User[users.size()]);
	}

}
