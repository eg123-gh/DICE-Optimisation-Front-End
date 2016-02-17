package it.polimi.diceH2020.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import javax.annotation.PostConstruct;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import it.polimi.diceH2020.SPACE4Cloud.shared.inputData.InstanceData;
import it.polimi.diceH2020.SPACE4Cloud.shared.inputData.TypeVMJobClassKey;
import it.polimi.diceH2020.SPACE4Cloud.shared.solution.Solution;

@Service
public class Experiment {
	private final Logger logger = Logger.getLogger(this.getClass().getName());

	private ObjectMapper mapper;
	@Autowired
	private Settings settings;
	private static String SOLUTION_ENDPOINT;
	private static String INPUTDATA_ENDPOINT;
	private static String EVENT_ENDPOINT;
	private static String STATE_ENDPOINT;
	private static String UPLOAD_ENDPOINT;
	private static String RESULT_FOLDER;

	public Experiment() {
		mapper = new ObjectMapper();
		SimpleModule module = new SimpleModule();
		module.addKeyDeserializer(TypeVMJobClassKey.class, TypeVMJobClassKey.getDeserializer());
		mapper.registerModule(module);
	}

	@PostConstruct
	private void init() throws IOException {
		INPUTDATA_ENDPOINT = settings.getfullAddress() + "/inputdata";
		EVENT_ENDPOINT = settings.getfullAddress() + "/event";
		STATE_ENDPOINT = settings.getfullAddress() + "/state";
		UPLOAD_ENDPOINT = settings.getfullAddress() + "/upload";
		SOLUTION_ENDPOINT = settings.getfullAddress() + "/solution";
		Path result = Paths.get(settings.getResultDir());
		Files.exists(result);
		// Files.deleteIfExists(result);
		Files.walkFileTree(result, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
		Files.deleteIfExists(result);
		Files.createDirectory(result);
		RESULT_FOLDER = result.toAbsolutePath().toString();

	}

	private RestTemplate restTemplate = new RestTemplate();

	public void launch(Path inputDataPath) {

		String baseErrorString = "Error for experiment: " + inputDataPath.getName(inputDataPath.getNameCount() - 1);

		boolean idle = checkWSIdle();

		if (!idle) {
			logger.info(baseErrorString + "-> service not idle");
			return;
		}

		boolean charged_inputdata = sendInputData(inputDataPath);

		if (!charged_inputdata) return;

		boolean charged_initsolution = generateInitialSolution();

		if (!charged_initsolution) {
			logger.info(baseErrorString + "-> generation of the initial solution");
			return;
		}

		boolean initsolution_saved = saveInitSolution();
		if (!initsolution_saved) {
			logger.info(baseErrorString + "-> getting or saving initial solution");
			return;
		}

		boolean finish = executeLocalSearch();

		if (!finish) {
			logger.info(baseErrorString + "-> local search");
			return;
		}

		boolean finalSolution_saved = saveFinalSolution();
		if (!finalSolution_saved) {
			logger.info(baseErrorString + "-> getting or saving final solution");
			return;
		}
		// to go to idle
		 restTemplate.postForObject(EVENT_ENDPOINT, Events.TO_RUNNING_INIT, String.class); 
		
	}

	private boolean saveFinalSolution() {
		Solution sol = restTemplate.getForObject(SOLUTION_ENDPOINT, Solution.class);
		String solFilePath = RESULT_FOLDER + File.separator + sol.getId() + "-final.json";
		String solSerialized;
		try {
			solSerialized = mapper.writeValueAsString(sol);
			Files.write(Paths.get(solFilePath), solSerialized.getBytes());
			System.out.println(sol.toStringReduced());
			logger.info(sol.toStringReduced());
			return true;
		} catch (JsonProcessingException e) {
			return false;
		}
		// System.out.println(serialized);
		catch (IOException e) {
			return false;
		}
	}

	private boolean executeLocalSearch() {
		String res = restTemplate.postForObject(EVENT_ENDPOINT, Events.TO_RUNNING_LS, String.class);
		if (res.equals("RUNNING_LS")) {
			res = "RUNNING_LS";
			while (res.equals("RUNNING_LS")) {
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				res = restTemplate.getForObject(STATE_ENDPOINT, String.class);
			}
		}
		if (res.equals("FINISH")) return true;
		else return false;
	}

	private boolean saveInitSolution() {
		Solution sol = restTemplate.getForObject(SOLUTION_ENDPOINT, Solution.class);
		String solFilePath = RESULT_FOLDER + File.separator + sol.getId() + "-MINLP.json";
		String solSerialized;
		try {
			solSerialized = mapper.writeValueAsString(sol);
			Files.write(Paths.get(solFilePath), solSerialized.getBytes());
			return true;
		} catch (JsonProcessingException e) {
			return false;
		}
		// System.out.println(serialized);
		catch (IOException e) {
			return false;
		}

	}

	private boolean generateInitialSolution() {
		String res = restTemplate.postForObject(EVENT_ENDPOINT, Events.TO_RUNNING_INIT, String.class);
		if (res.equals("RUNNING_INIT")) {
			res = "RUNNING_INIT";
			while (res.equals("RUNNING_INIT")) {
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				res = restTemplate.getForObject(STATE_ENDPOINT, String.class);
			}
			if (res.equals("CHARGED_INITSOLUTION")) {
				return true;
			} else return false;
		} else return false;
	}

	private boolean sendInputData(Path inputDataPath) {
		InstanceData data = getObjectFromPath(inputDataPath);

		if (data != null) {
			String res = restTemplate.postForObject(INPUTDATA_ENDPOINT, data, String.class);
			if (res.equals("CHARGED_INPUTDATA")) return true;
			else {
				logger.info("Error for experiment: " + inputDataPath.getName(inputDataPath.getNameCount() - 1) + " server respondend in an unexpected way: " + res);
				return false;
			}

		} else {
			logger.info("Error for experiment: " + inputDataPath.getName(inputDataPath.getNameCount() - 1) + " problem in inputdata serialization");
			return false;
		}
	}

	private InstanceData getObjectFromPath(Path inputDataPath) {
		String serialized;
		try {
			serialized = new String(Files.readAllBytes(inputDataPath));
			InstanceData data = mapper.readValue(serialized, InstanceData.class);
			return data;
		} catch (IOException e) {
			return null;
		}

	}

	private boolean checkWSIdle() {
		String res = restTemplate.getForObject(STATE_ENDPOINT, String.class);
		if (res.equals("IDLE")) return true;
		else return false;

	}

	public void send(Path f) {
		MultiValueMap<String, Object> map = new LinkedMultiValueMap<String, Object>();
		String content;
		try {
			content = new String(Files.readAllBytes(f));

			final String filename = f.getFileName().toString();
			map.add("name", filename);
			map.add("filename", filename);
			ByteArrayResource contentsAsResource = new ByteArrayResource(content.getBytes("UTF-8")) {
				@Override
				public String getFilename() {
					return filename;
				}
			};
			map.add("file", contentsAsResource);
			String result = restTemplate.postForObject(UPLOAD_ENDPOINT, map, String.class);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
