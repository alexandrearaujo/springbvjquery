package fr.fcamblor.demos.sbjd.web;

import com.jayway.restassured.response.Response;
import fr.fcamblor.demos.sbjd.models.Address;
import fr.fcamblor.demos.sbjd.models.Credentials;
import fr.fcamblor.demos.sbjd.models.User;
import fr.fcamblor.demos.sbjd.test.rules.RequiresDefaultRestAssuredConfiguration;
import fr.fcamblor.demos.sbjd.test.rules.RequiresRunningEmbeddedTomcat;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.http.HttpStatus;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.jayway.restassured.RestAssured.expect;
import static com.jayway.restassured.RestAssured.given;

/**
 * @author fcamblor
 */
public class RegistrationControllerTest {

    private static final int VALIDATION_ERROR_HTTP_STATUS_CODE = HttpStatus.PRECONDITION_FAILED.value();
    private static final int VALIDATION_OK_HTTP_STATUS_CODE = HttpStatus.OK.value();

    @Rule
    // Ensuring a tomcat server will be up and running during test execution !
    public RequiresRunningEmbeddedTomcat tomcat = new RequiresRunningEmbeddedTomcat();

    @Rule
    public RequiresDefaultRestAssuredConfiguration raDefaultConfig = new RequiresDefaultRestAssuredConfiguration();

    @Test
    public void wellFormedUserShouldBeAcceptedInCreation(){
        expectCreateUserStatus(createWellFormedUserForCreation(), VALIDATION_OK_HTTP_STATUS_CODE);
    }

    @Test
    public void nullFirstNameShouldntBeAcceptedInCreation(){
        expectCreateUserStatus(createWellFormedUserForCreation().setFirstName(null), VALIDATION_ERROR_HTTP_STATUS_CODE);
        expectCreateUserStatus(createWellFormedUserForCreation().setFirstName("blah"), VALIDATION_OK_HTTP_STATUS_CODE);
    }

    @Test
    public void nullIdShouldntBeAcceptedInUpdate(){
        // Creating a user to be able to edit it laterly...
        User existingUser =  createUser(createWellFormedUserForCreation());
        // ... everything should be ok from now..

        // Editing non existing user (null id) should throw a validation error
        expectUpdateUserStatus(createWellFormedUserForCreation(), VALIDATION_ERROR_HTTP_STATUS_CODE);
        // Editing existing user should be ok
        expectUpdateUserStatus(createWellFormedUserForCreation().setId(existingUser.getId()), VALIDATION_OK_HTTP_STATUS_CODE);
    }

    @Test
    public void futureBirthDateShouldntBeAllowed(){
        Calendar futureCalendar = new GregorianCalendar();
        futureCalendar.add(Calendar.DAY_OF_YEAR, 1);
        expectCreateUserStatus(createWellFormedUserForCreation().setBirthDate(futureCalendar.getTime()), VALIDATION_ERROR_HTTP_STATUS_CODE);
        futureCalendar.add(Calendar.DAY_OF_YEAR, -2);
        futureCalendar.add(Calendar.YEAR, -18);
        expectCreateUserStatus(createWellFormedUserForCreation().setBirthDate(futureCalendar.getTime()), VALIDATION_OK_HTTP_STATUS_CODE);
    }

    @Test
    public void userAdressesShouldBeNullables(){
        expectCreateUserStatus(createWellFormedUserForCreation().setAddresses(null), VALIDATION_OK_HTTP_STATUS_CODE);
    }

    @Test
    public void userPhoneNumberShouldNeverBeNullNorEmpty(){
        expectCreateUserStatus(createWellFormedUserForCreation().setPhoneNumbers(null), VALIDATION_ERROR_HTTP_STATUS_CODE);
        expectCreateUserStatus(createWellFormedUserForCreation().setPhoneNumbers(Collections.<String>emptyList()), VALIDATION_ERROR_HTTP_STATUS_CODE);
    }

    @Test
    public void adressStreet2ShouldBeEitherNullOrSizedWith5CharsMin(){
        Address address = createWellFormedAddress();
        User user = createWellFormedUserForCreation().setAddresses(Arrays.asList( address ));

        expectCreateUserStatus(user, VALIDATION_OK_HTTP_STATUS_CODE); // Default address should be ok
        address.setStreet2(null);
        expectCreateUserStatus(user, VALIDATION_OK_HTTP_STATUS_CODE); // null street2 should be ok
        address.setStreet2("A long street 2");
        expectCreateUserStatus(user, VALIDATION_OK_HTTP_STATUS_CODE); // long street2 should be ok
        address.setStreet2("aaa");
        expectCreateUserStatus(user, VALIDATION_ERROR_HTTP_STATUS_CODE); // short street2 should _not_ be ok
    }

    @Test
    public void nonAdultUserShouldNotBeAllowed(){
        Calendar birthCalendar = new GregorianCalendar();
        birthCalendar.add(Calendar.YEAR, -17);
        expectCreateUserStatus(createWellFormedUserForCreation().setBirthDate(birthCalendar.getTime()), VALIDATION_ERROR_HTTP_STATUS_CODE);
    }

    @Test
    public void beanValidationShouldActStrangelyOnArraysAndCollectionsByNotValidatingThem(){
        Address[] invalidAddresses = new Address[]{
                new Address(),
                new Address()
        };
        int strangeHttpCode = HttpStatus.OK.value(); // Yup, this is strange... I would have expected 412..
        putJSONAndExpectStatus("/users/123/addresses", invalidAddresses, strangeHttpCode);
        putJSONAndExpectStatus("/users/123/addressList", invalidAddresses, strangeHttpCode);
    }

    protected User createUser(User userToCreate){
        return postJSONAndExpectStatus("/users", userToCreate, HttpStatus.OK.value()).as(User.class);
    }

    protected Response expectCreateUserStatus(User userToCreate, int expectedStatusCode){
        return postJSONAndExpectStatus("/users", userToCreate, expectedStatusCode);
    }

    protected Response expectUpdateUserStatus(User userToCreate, int expectedStatusCode){
        return putJSONAndExpectStatus("/users/registered", userToCreate, expectedStatusCode);
    }

    protected <T> T getObjectAndExpectStatus(String url, Class<T> clazz, int expectedStatusCode){
        return
        expect().
                statusCode(expectedStatusCode).
        when().
                get(url).as(clazz);
    }

    protected Response postJSONAndExpectStatus(String url, Object objectToPut, int expectedStatusCode){
        return
        given().
                contentType("application/json").
                body(objectToPut).
        expect().
                statusCode(expectedStatusCode).
        when().
                post(url);
    }

    protected Response putJSONAndExpectStatus(String url, Object objectToPut, int expectedStatusCode){
        return
        given().
                contentType("application/json").
                body(objectToPut).
        expect().
                statusCode(expectedStatusCode).
        when().
                put(url);
    }

    protected static Address createWellFormedAddress(){
        return new Address().
                setStreet1("37 rue Jean Moulin").
                setPostalCode("33140").
                setCity("Villenave d'Ornon");
    }

    protected static Credentials createWellFormedCredentials(){
        return new Credentials().setLogin("foo@bar.com").setPassword("bar");
    }

    protected static User createWellFormedUserForCreation(){
        Date birthDate = new Date();
        try { birthDate = new SimpleDateFormat("dd/MM/yyyy").parse("29/06/1983"); }
        catch (ParseException e) {}
        return new User().
                setFirstName("Frédéric").
                setLastName("Camblor").
                setBirthDate(birthDate).
                setPhoneNumbers(Arrays.asList("0123456789")).
                setCredentials(createWellFormedCredentials()).
                setAddresses(Collections.<Address>emptyList());
    }
}
