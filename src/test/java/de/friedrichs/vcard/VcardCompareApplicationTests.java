package de.friedrichs.vcard;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

@SpringBootTest
class VcardCompareApplicationTests {

	@Autowired
	private IOController ioController;

	@Test
	void contextLoads() throws IOException {
		assert !ioController.getVCards().isEmpty();
		ioController.diff();
	}

}
