package edu.rutgers.dimacs.reu;

import static javax.ejb.LockType.READ;

import javax.ejb.Lock;
import javax.ejb.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import edu.rutgers.dimacs.reu.utility.SQLiteHandler;

@Singleton
@Path("info")
@Lock(READ)
public class Info {
	@GET
	@Produces(MediaType.TEXT_HTML)
	public String get() {
		return SQLiteHandler.getInstance().getInfo();
	}
}
