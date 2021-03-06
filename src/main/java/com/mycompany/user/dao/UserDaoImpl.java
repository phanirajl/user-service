/*
 * |-------------------------------------------------
 * | Copyright © 2018 Colin But. All rights reserved. 
 * |-------------------------------------------------
 */
package com.mycompany.user.dao;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.LocalDate;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.mycompany.user.dao.mapper.CassandraRowMapper;
import com.mycompany.user.model.User;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

@Repository
public class UserDaoImpl implements UserDao {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserDaoImpl.class);

    private static final String SELECT_ALL_USERS_QUERY = "SELECT * FROM userKS.USERS";
    private static final String SELECT_USER_WITH_SSN = "SELECT * FROM userKS.USERS where SSN = ";
    private static final String DELETE_USER = "DELETE FROM userKS.USERS where SSN = ";
    private static final String INSERT_USER = "INSERT INTO userKS.USERS (ssn, forename, surname, dob, address, postcode, city, country) values ('%s', '%s', '%s', '%s', '%s', '%s', '%s', '%s')";

    private Cluster cluster = null;
    private Session session = null;

    private SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");

    @Value("${db.cassandra.host:127.0.0.1}")
    private String dbHost;

    private UserRowMapper userRowMapper = new UserRowMapper();

    @PostConstruct
    public void setUp(){

        String dbHost = System.getenv("CASSANDRA_HOST");

        cluster = Cluster.builder().addContactPoint(dbHost == null ? this.dbHost : dbHost).build();
        session = cluster.connect();
    }

    @PreDestroy
    public void tearDown() {
        if (cluster != null) {
            cluster.close();
        }
    }

    @Override
    public List<User> getUsers() {

        List<User> users = new ArrayList<User>();

        LOGGER.info("Executing CQL: {}", SELECT_ALL_USERS_QUERY);

        ResultSet rs = session.execute(SELECT_ALL_USERS_QUERY);

        for (Row row : rs) {
            User user = userRowMapper.mapRow(row);

            LOGGER.debug("Retrieved user {} from datastore", user);

            users.add(user);
        }

        return users;
    }

    @Override
    public User getUser(String ssn) {

        String selectUserWithSsn = SELECT_USER_WITH_SSN + ssn;

        LOGGER.info("Executing CQL: {}", selectUserWithSsn);

        ResultSet rs = session.execute(selectUserWithSsn);
        Row row = rs.one();

        User user = userRowMapper.mapRow(row);

        LOGGER.info("Retrieved user %s from datastore", user);

        return user;
    }

    @Override
    public void saveUser(User user) {
        String saveUserCql = String.format(INSERT_USER, user.getSsn(), user.getFirstname(), user.getSurname(),
            formatter.format(user.getDob()), user.getPostcode(), user.getAddress(), user.getCity(), user.getCountry());

        LOGGER.info("Executing CQL: {}", saveUserCql);

        session.execute(saveUserCql);
    }

    @Override
    public void deleteUser(String ssn) {
        String deleteUserWithSsn = DELETE_USER + ssn;

        LOGGER.info("Executing CQL: {}" + deleteUserWithSsn);

        session.execute(deleteUserWithSsn);
    }

    private static class UserRowMapper implements CassandraRowMapper{

        @Override
        public User mapRow(Row row) {
            User user = new User();
            user.setSsn(row.getString("ssn"));
            user.setFirstname(row.getString("forename"));
            user.setSurname(row.getString("surname"));

            LocalDate dateFromDB = row.getDate("dob");
            DateTime dateTime = new DateTime(dateFromDB.getYear(), dateFromDB.getMonth(), dateFromDB.getDay(), 0, 0, 0);

            user.setDob(dateTime.toDate());
            user.setAddress(row.getString("address"));
            user.setPostcode(row.getString("postcode"));
            user.setCity(row.getString("city"));
            user.setCountry(row.getString("country"));
            return user;
        }
    }

}
