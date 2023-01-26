package open_xml_reader;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLStreamException;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class is used to read the sheets data of
 * a workbook (open xml format) and then to return its parsed data
 * into a {@link ResultDataSet}.
 * @author avonva
 *
 */
public class WorkbookReader implements AutoCloseable {
	
	private static final Logger LOGGER = LogManager.getLogger(WorkbookReader.class);

	private int rowCount = -1;
	private BufferedSheetReader sheetParser;
	private InputStream sheetReader;
	private XSSFReader reader = null;
	private WorkbookHandler workbookHandler = null;
	private OPCPackage pkg = null;

	/**
	 * Initialize a workbook reader
	 * @param filename the name of the workbook file
	 * @throws IOException
	 * @throws OpenXML4JException
	 * @throws SAXException
	 */
	public WorkbookReader(String filename) 
			throws IOException, OpenXML4JException, SAXException {
		
		LOGGER.info("Filename of the workbook file " + filename);

		// open the workbook in open xml format
		pkg = OPCPackage.open(filename, PackageAccess.READ);

		// read it
		reader = new XSSFReader(pkg);

		// get its data and parse them to retrieve
		// the sheet information (relationshipId)
		InputStream wbStream = reader.getWorkbookData();
		workbookHandler = new WorkbookHandler();

		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser = null;
		try {
			parser = factory.newSAXParser();
			parser.parse(new InputSource(wbStream), workbookHandler);
		} catch (ParserConfigurationException | SAXException e) {
			LOGGER.error("There are errors on parsing stream ", e);
			e.printStackTrace();
		}

		wbStream.close();
	}


	/**
	 * Process a single sheet of the workbook. Note that this
	 * method will override {@link #sheetParser} in order to
	 * parse the new sheet. Be sure that if another {@link #sheetParser}
	 * was created before that it has finished its work.
	 * @param name
	 * @throws IOException 
	 * @throws InvalidFormatException 
	 * @throws XMLStreamException 
	 */
	public void processSheetName(String name) throws IOException, 
	InvalidFormatException, XMLStreamException {

		// close the previous sheet reader if there was one
		if (sheetReader != null)
			sheetReader.close();

		if (sheetParser != null)
			sheetParser.clear();

		// get the sheet relationship id using the sheet name
		String sheetRId = workbookHandler.getSheetRelationshipId(name);

		// if not sheet id is retrieved => exception
		if (sheetRId.equals("") || sheetRId == null) {
			LOGGER.error("No sheet named " + name + " was found!");
			return;
		}

		// get the sheet from the reader
		sheetReader = reader.getSheet(sheetRId);

		// create a parser with pull pattern
		sheetParser = new BufferedSheetReader (sheetReader, 
				reader.getSharedStringsTable());
		
		// get the number of rows for the sheet
		InputStream input = reader.getSheet(sheetRId);
		rowCount = BufferedSheetReader.getRowCount(input);
		input.close();
	}

	/**
	 * The parser has other nodes to parse?
	 * @return
	 */
	public boolean hasNext() {

		if (sheetParser == null)
			return false;

		return sheetParser.hasNext();
	}

	/**
	 * Get the number of rows for the current sheet
	 * Note that you must call {@link #processSheetName(String)}
	 * to have a consistent result.
	 * @return
	 */
	public int getRowCount() {
		return rowCount;
	}
	
	/**
	 * Set the batch size of the current
	 * {@link #sheetParser}. Using {@link #next()}
	 * will create a {@link ResultDataSet} with
	 * only {@code batchSize} rows processed
	 * at a time (you need to call {@link #next()}
	 * until the data are finished!)
	 * @param batchSize
	 */
	public void setBatchSize(int batchSize) {

		if (sheetParser == null)
			return;

		sheetParser.setBatchSize(batchSize);
	}
	public BufferedSheetReader getSheetParser() {
		return sheetParser;
	}
	/**
	 * Get the next batch result set from the parser
	 * @return
	 * @throws XMLStreamException
	 */
	public ResultDataSet next() throws XMLStreamException {

		if (sheetParser == null)
			return null;

		LOGGER.debug("Next batch result set " + sheetParser.next());
		return sheetParser.next();
	}

	/**
	 * Close the dataset
	 * @throws XMLStreamException 
	 * @throws IOException 
	 */
	public void close() throws XMLStreamException, IOException {
		
		if (sheetParser != null)
			sheetParser.close();

		sheetReader.close();
		pkg.close();
	}
}
