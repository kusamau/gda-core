/*-
 * Copyright © 2013 Diamond Light Source Ltd.
 *
 * This file is part of GDA.
 *
 * GDA is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License version 3 as published by the Free
 * Software Foundation.
 *
 * GDA is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along
 * with GDA. If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.gda.beamline.synoptics.composites;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.eclipse.dawnsci.plotting.api.PlotType;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.swtdesigner.SWTResourceManager;

import gda.device.detectorfilemonitor.FileProcessor;
import gda.observable.IObserver;
import uk.ac.diamond.daq.concurrent.Async;
import uk.ac.gda.beamline.synoptics.api.PlotConfigurable;
import uk.ac.gda.beamline.synoptics.events.DirectoryChangeEvent;
import uk.ac.gda.beamline.synoptics.events.LatestFilenameEvent;
import uk.ac.gda.beamline.synoptics.utils.DataDirectoryMonitor;
/**
 * A {@link Composite} provides a control view of monitoring latest detector data file
 * or a control to play back detector data files already collected in a specified data directory.
 * <ul>
 * <li>When 'play' is selected, it tracks the latest detector data file collected;</li>
 * <li>When 'pause' is enabled, one can display data file already collected previously;</li>
 * <li>Selected data file will be displayed in a plot view specified as a file processor</li>
 * <li>support switching of plot type (see {@link PlotType}) for different views of the same data files;</li>
 * <li>allow plot each data file as new plot or plot over multiple data files on the same graph.</li>
 * </ul>
 */
class LatestFilenameComposite extends Composite {

	private static final String ALL_DATA = "All Data";

	private static final String AUTO_SKIP_TO_LATEST = "Auto skip to latest";

	private static final String WAITING = "Waiting...";

	public static final String EMPTY = "";
	private static final Logger logger = LoggerFactory.getLogger(LatestFilenameComposite.class);

	private DataDirectoryMonitor dirWatcher;

	private Text fileNameText;
	private Text textIndex;
	private FileProcessor fileProcessor;
	private IObserver observer;
	private Button btnSkipToStart;
	private Button btnSkipToLatest;
	private Button btnBackOne;
	private Button btnForwardOne;

	// Used when there's just one combined play/pause button
	private Button btnShowLatest;

	// Used when there are separate play/pause buttons
	private Button playButton;
	private Button pauseButton;

	private Integer latestFoundIndex = null;

	boolean selectLatestFoundIndex = true;

	Integer lastSelectedIndex = null;

	private Group group;

	private String label;

	private Image toStartImage;
	private Image toLatestImage;
	private Image pauseImage;
	private Image runImage;
	private Image backOneImage;
	private Image forwardOneImage;
	private boolean showButtonSeparator;
	private boolean separatePlayPauseButtons;

	private int startNumber;

	private Button newPlotButton;

	private Combo plotTypes;

	private Map<String, Predicate<String>> detectors;

	private Combo detectorCombo;

	protected String selectedDetector = ALL_DATA;

	public void setLabel(String label) {
		this.label = label;
	}

	public void setFileProcessor(FileProcessor fileProcessor) {
		this.fileProcessor = fileProcessor;
	}

	public void setShowButtonSeparator(boolean showButtonSeparator) {
		this.showButtonSeparator = showButtonSeparator;
	}

	public void setSeparatePlayPauseButtons(boolean separatePlayPauseButtons) {
		this.separatePlayPauseButtons = separatePlayPauseButtons;
	}

	public void setImages(Image toStartImage, Image toLatestImage, Image pauseImage, Image runImage,
			Image backOneImage, Image forwardOneImage) {
		this.toStartImage = toStartImage;
		this.toLatestImage = toLatestImage;
		this.pauseImage = pauseImage;
		this.runImage = runImage;
		this.backOneImage = backOneImage;
		this.forwardOneImage = forwardOneImage;
	}

	public LatestFilenameComposite(Composite parent, int style) {
		super(parent, style);
	}

	public LatestFilenameComposite(Composite parent, int style, String label, final FileProcessor fileProcessor,
			Image toStartImage, Image toLatestImage, final Image pauseImage, final Image runImage, Image backOneImage,
			Image forwardOneImage) {
		super(parent, style);
		setLabel(label);
		setFileProcessor(fileProcessor);
		setImages(toStartImage, toLatestImage, pauseImage, runImage, backOneImage, forwardOneImage);
		createControls();
	}

	public void createControls() {
		GridLayoutFactory.fillDefaults().numColumns(1).applyTo(this);
		GridDataFactory.fillDefaults().applyTo(this);

		int numColumns = 8 + (showButtonSeparator ? 1 : 0) + (separatePlayPauseButtons ? 2 : 1);

		group = new Group(this, SWT.NONE);
		group.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		group.setText(label);
		GridLayoutFactory.swtDefaults().numColumns(numColumns).applyTo(group);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(group);
		group.setToolTipText("The latest detector filename is displayed. Press pause to scrolling back through all filenames in the current collection");

		fileNameText = new Text(group, SWT.SINGLE | SWT.BORDER);
		fileNameText.setText(WAITING);
		fileNameText.setEditable(true);
		GridDataFactory.fillDefaults().span(numColumns, 1).grab(true, false).applyTo(fileNameText);
		fileNameText.addKeyListener(new KeyAdapter() {

			@Override
			public void keyReleased(KeyEvent e) {
				if (e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR) {
					processFilenameEntered();
				}
			}
		});

		btnSkipToStart = new Button(group, SWT.PUSH);
		btnSkipToStart.setToolTipText("Skip to first file");
		btnSkipToStart.setImage(toStartImage);
		GridDataFactory.fillDefaults().grab(false, false).applyTo(btnSkipToStart);
		btnSkipToStart.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				setSelectLatestFoundIndex(false);
				setSelectedIndex(getStartNumber());
			}
		});

		btnBackOne = new Button(group, SWT.PUSH);
		btnBackOne.setToolTipText("Back One File");
		btnBackOne.setImage(backOneImage);
		GridDataFactory.fillDefaults().grab(false, false).applyTo(btnBackOne);
		btnBackOne.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				setSelectLatestFoundIndex(false);
				setSelectedIndex(Math.max(getStartNumber(), getSelectedIndex() - 1));
			}
		});

		textIndex = new Text(group, SWT.SINGLE | SWT.BORDER);
		GridDataFactory.fillDefaults().grab(true, false).minSize(50, 1).applyTo(textIndex);
		textIndex.addFocusListener(new FocusListener() {

			@Override
			public void focusLost(FocusEvent e) {
				processTextIndex();
			}

			@Override
			public void focusGained(FocusEvent e) {
				textIndex.selectAll();
			}
		});

		textIndex.addVerifyListener(new VerifyListener() {

			@Override
			public void verifyText(VerifyEvent e) {
				if (latestFoundIndex == null) {
					e.doit = false;
				} else {
					e.doit = e.text.isEmpty() || e.text.matches("[0-9]+");
				}
			}
		});

		textIndex.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				if (e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR) {
					processTextIndex();
				}
			}
		});

		btnForwardOne = new Button(group, SWT.PUSH);
		btnForwardOne.setToolTipText("Forward One File");
		btnForwardOne.setImage(forwardOneImage);
		GridDataFactory.fillDefaults().grab(false, false).applyTo(btnForwardOne);
		btnForwardOne.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				setSelectLatestFoundIndex(false);
				setSelectedIndex(Math.min(latestFoundIndex, getSelectedIndex() + 1));
			}
		});

		btnSkipToLatest = new Button(group, SWT.PUSH);
		btnSkipToLatest.setToolTipText("Skip to latest file");
		btnSkipToLatest.setImage(toLatestImage);
		GridDataFactory.fillDefaults().grab(false, false).applyTo(btnSkipToLatest);
		btnSkipToLatest.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				setSelectLatestFoundIndex(false);
				setSelectedIndex(latestFoundIndex);
			}
		});

		if (showButtonSeparator) {
			final Label separator = new Label(group, SWT.SEPARATOR | SWT.VERTICAL);
			GridDataFactory.fillDefaults().hint(SWT.DEFAULT, 0).grab(false, false).applyTo(separator);
		}

		if (!separatePlayPauseButtons) {
			btnShowLatest = new Button(group, SWT.TOGGLE);
			btnShowLatest.setToolTipText(AUTO_SKIP_TO_LATEST);
			btnShowLatest.setImage(pauseImage);
			GridDataFactory.fillDefaults().grab(false, false).applyTo(btnShowLatest);
			btnShowLatest.addSelectionListener(new SelectionAdapter() {

				@Override
				public void widgetSelected(SelectionEvent e) {
					boolean selection = btnShowLatest.getSelection();
					// selected means pause
					setPlaying(!selection);
				}
			});
		} else {
			playButton = new Button(group, SWT.TOGGLE);
			playButton.setToolTipText(AUTO_SKIP_TO_LATEST);
			playButton.setImage(runImage);
			GridDataFactory.fillDefaults().grab(false, false).applyTo(playButton);

			pauseButton = new Button(group, SWT.TOGGLE);
			pauseButton.setToolTipText("Manual selection");
			pauseButton.setImage(pauseImage);
			GridDataFactory.fillDefaults().grab(false, false).applyTo(pauseButton);

			playButton.setSelection(true);

			playButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					final boolean playSelected = playButton.getSelection();
					setPlaying(playSelected);
				}
			});

			pauseButton.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					final boolean pauseSelected = pauseButton.getSelection();
					setPlaying(!pauseSelected);
				}
			});
		}

		// plot - checked - clear first, unchecked - plot over
		newPlotButton = new Button(group, SWT.CHECK);
		newPlotButton.setBackground(SWTResourceManager.getColor(SWT.COLOR_TRANSPARENT));
		newPlotButton.setToolTipText("clear first?");
		newPlotButton.setSelection(true);// default to checked - always clear first.
		GridDataFactory.fillDefaults().grab(false, false).applyTo(newPlotButton);

		plotTypes = new Combo(group, SWT.DROP_DOWN | SWT.READ_ONLY);
		plotTypes.setToolTipText("Plot type to use");
		for (PlotType type : PlotType.values()) {
			plotTypes.add(type.name());
		}
		plotTypes.select(PlotType.XY.ordinal()); // default type
		GridDataFactory.fillDefaults().grab(false, false).applyTo(plotTypes);
		plotTypes.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				processSelectedPlotType();
			}
		});

		detectorCombo = new Combo(group, SWT.DROP_DOWN | SWT.READ_ONLY);
		detectorCombo.setToolTipText("Select a detector to filter");
		detectorCombo.add(ALL_DATA);
		for (String detector : getDetectors().keySet()) {
			detectorCombo.add(detector);
		}
		detectors.put(ALL_DATA, a -> true);
		detectorCombo.select(0); // default to no filtering of data
		GridDataFactory.fillDefaults().grab(false, false).applyTo(plotTypes);
		detectorCombo.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				selectedDetector = detectorCombo.getText();
				if (detectorCombo.getSelectionIndex() == 0 && !dirWatcher.getDataFilesCollected().isEmpty()) {
					latestFoundIndex = dirWatcher.getDataFilesCollected().size() - 1;
				} else {
					List<Path> detectorFilteredFileList = detectorFilteredFileList(detectorCombo.getText());
					if (!detectorFilteredFileList.isEmpty()) {
						latestFoundIndex = detectorFilteredFileList.size() - 1;
					} else {
						latestFoundIndex = null;
					}
				}
				if (latestFoundIndex != null) {
					if (getSelectedIndex() > latestFoundIndex || playButton.getSelection()) {
						setSelectLatestFoundIndex(false);
						setSelectedIndex(latestFoundIndex);
					} else {
						setSelectLatestFoundIndex(false);
						// must re-process the index as file list changed.
						setSelectedIndex(getSelectedIndex());
					}
				} else {
					fileNameText.setText(WAITING);
					textIndex.setText("");
				}
				enableBtns();
			}
		});

		setSelectedIndex(getStartNumber());
		// initialize();

		setVisible(true);

		observer = new IObserver() {
			@Override
			public void update(Object source, final Object arg) {
				if (arg instanceof LatestFilenameEvent) {
					latestFoundIndex = ((LatestFilenameEvent) arg).getIndex();
					final String filename = ((LatestFilenameEvent) arg).getFilename();
					logger.debug("New detector file: {}", filename);
					PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
							if (pauseButton.getSelection()) {
								// paused, no update the latest Detector file.
								enableBtns();
								return;
							}
							List<Path> detectorFiles = detectorFilteredFileList(detectorCombo.getText());
							if (detectors.get(detectorCombo.getText()).test(filename)) {
								latestFoundIndex = detectorFiles.size() -1;
							}
							group.setText(label);
							fileNameText.setText(filename);
							setSelectedIndex(latestFoundIndex);
					});
				} else if (arg instanceof DirectoryChangeEvent) {
					Async.execute(() -> {
						List<Path> files = detectorFilteredFileList(selectedDetector);
						PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
							if (files.isEmpty()) {
								latestFoundIndex = null;
								textIndex.setText("");
								fileNameText.setText(WAITING);
							} else {
								latestFoundIndex = files.size() - 1;
								String lastFile = files.get(latestFoundIndex).toString();
								fileNameText.setText(lastFile);
								setSelectedIndex(latestFoundIndex);
							}
							enableBtns();
						});
					});
				}
			}
		};

		dirWatcher.addIObserver(observer);

		addDisposeListener(new DisposeListener() {
			@Override
			public void widgetDisposed(DisposeEvent e) {
				dirWatcher.deleteIObserver(observer);
			}
		});
	}

	protected void processFilenameEntered() {
		String newFilename = fileNameText.getText().trim();
		if (!Files.exists(Paths.get(newFilename))) {
			MessageBox msgbox = new MessageBox(getShell(), SWT.ICON_ERROR);
			msgbox.setMessage("File: " + newFilename + " is not found.");
			msgbox.open();
			return;
		}

		if (fileProcessor instanceof PlotConfigurable) {
			((PlotConfigurable) fileProcessor).setNewPlot(newPlotButton.getSelection());
			((PlotConfigurable) fileProcessor).setPlotType(PlotType.values()[plotTypes.getSelectionIndex()]);
		}
		fileProcessor.processFile(newFilename);

		int findTextIndex = -1;
		if (detectorCombo.getSelectionIndex() == 0) {
			findTextIndex = findTextIndex(dirWatcher.getDataFilesCollected());
		} else {
			findTextIndex = findTextIndex(detectorFilteredFileList(detectorCombo.getText()));
		}
		if (findTextIndex == -1)
			return;
		updateIndexText(findTextIndex);
		enableBtns();
	}

	private int findTextIndex(List<Path> dataFileCollected) {
		return dataFileCollected.indexOf(Paths.get(fileNameText.getText()));
	}

	private void processSelectedIndex() {
		if (latestFoundIndex == null)
			return;
		int index = getSelectedIndex();
		String filename = null;
		if (detectorCombo.getSelectionIndex() == 0 && !dirWatcher.getDataFilesCollected().isEmpty()) {
			if (index < dirWatcher.getDataFilesCollected().size()) {
				filename = dirWatcher.getDataFilesCollected().get(index).toString();
			}
		} else {
			List<Path> detectorFilteredFileList = detectorFilteredFileList(detectorCombo.getText());
			if (!detectorFilteredFileList.isEmpty()) {
				if (index<detectorFilteredFileList.size()) {
					filename = detectorFilteredFileList.get(index).toString();
				}
			}
		}
		if (filename != null) {
			fileNameText.setText(filename);
			try {
				if (fileProcessor instanceof PlotConfigurable) {
					((PlotConfigurable) fileProcessor).setNewPlot(newPlotButton.getSelection());
					((PlotConfigurable) fileProcessor).setPlotType(PlotType.values()[plotTypes.getSelectionIndex()]);
				}
				fileProcessor.processFile(filename);
			} catch (Exception e) {
				logger.error("Couldn't plot data from {}", filename, e);
			}
		} else {
			fileNameText.setText(WAITING);
			textIndex.setText("");
		}
	}

	/**
	 * re-process file using new plot type with out change data sets.
	 */
	protected void processSelectedPlotType() {
		if (latestFoundIndex == null)
			return;
		if (fileProcessor instanceof PlotConfigurable) {
			((PlotConfigurable) fileProcessor).setNewPlot(newPlotButton.getSelection());
			((PlotConfigurable) fileProcessor).setPlotType(PlotType.values()[plotTypes.getSelectionIndex()]);
		}
		fileProcessor.processFile(null);
	}

	private void setPlaying(boolean playing) {
		setSelectLatestFoundIndex(playing);

		if (!separatePlayPauseButtons) {
			btnShowLatest.setImage(selectLatestFoundIndex ? pauseImage : runImage);
			btnShowLatest.setToolTipText(selectLatestFoundIndex ? "Manual selection" : AUTO_SKIP_TO_LATEST);
		} else {
			playButton.setSelection(playing);
			pauseButton.setSelection(!playing);
		}
		if (!playing) {
			group.setText("Selected Detector File");
		} else {
			group.setText(label);
		}
	}

	protected void setSelectLatestFoundIndex(boolean selectLatestFoundIndex) {
		if (this.selectLatestFoundIndex != selectLatestFoundIndex) {
			this.selectLatestFoundIndex = selectLatestFoundIndex;
			if (selectLatestFoundIndex && latestFoundIndex != null) {
				setSelectedIndex(latestFoundIndex);
			}
			enableBtns();
		}
	}

	void setSelectedIndex(int selected) {
		updateIndexText(selected);
		processSelectedIndex();
		enableBtns();
	}

	private void updateIndexText(int selected) {
		lastSelectedIndex = selected;
		textIndex.setText(Integer.toString(selected));
	}

	int getSelectedIndex() {
		return lastSelectedIndex;
	}

	void enableBtns() {
		boolean fileFound = latestFoundIndex != null;
		boolean doNotShowLatestAndFileFound = !selectLatestFoundIndex && fileFound;
		if (doNotShowLatestAndFileFound) {
			int selectedIndex = getSelectedIndex();
			btnSkipToStart.setEnabled(selectedIndex > getStartNumber());
			btnBackOne.setEnabled(selectedIndex > getStartNumber());
			btnForwardOne.setEnabled(selectedIndex < latestFoundIndex);
			btnSkipToLatest.setEnabled(selectedIndex < latestFoundIndex);
		} else {
			btnSkipToStart.setEnabled(false);
			btnBackOne.setEnabled(false);
			btnForwardOne.setEnabled(false);
			btnSkipToLatest.setEnabled(false);
		}
		textIndex.setEnabled(doNotShowLatestAndFileFound);
	}

	public void initialize() {
		List<Path> dataFileCollected = dirWatcher.getDataFilesCollected();
		if (!dataFileCollected.isEmpty()) {
			latestFoundIndex = dataFileCollected.size() - 1;
		} else {
			latestFoundIndex = null;
		}
		btnSkipToStart.setToolTipText("Skip to first - " + getStartNumber());

		if (latestFoundIndex != null)
			btnSkipToLatest.setToolTipText("Skip to latest - " + latestFoundIndex);

		if (selectLatestFoundIndex) {
			if (latestFoundIndex != null) {
				textIndex.setText(Integer.toString(latestFoundIndex));
				fileNameText.setText(dirWatcher.getDataFilesCollected().get(latestFoundIndex).toString());
			} else {
				fileNameText.setText(WAITING);
				textIndex.setText("");
			}
		}
		enableBtns();
	}

	private void processTextIndex() {
		if (latestFoundIndex != null) {
			try {
				Integer input = new Integer(textIndex.getText());
				if (!(input >= getStartNumber() && input <= latestFoundIndex))
					throw new Exception("Out of range");
				setSelectedIndex(input);
			} catch (Exception ex) {
				textIndex.setText(lastSelectedIndex != null ? lastSelectedIndex.toString() : "");
			}
		}
	}

	public int getStartNumber() {
		return startNumber;
	}

	public void setStartNumber(int startNumber) {
		this.startNumber = startNumber;
	}

	public DataDirectoryMonitor getDirWatcher() {
		return dirWatcher;
	}

	public void setDirWatcher(DataDirectoryMonitor dirWatcher) {
		this.dirWatcher = dirWatcher;
	}

	public Map<String, Predicate<String>> getDetectors() {
		return detectors;
	}

	/**
	 * Set map of detectors with regex string to filter on
	 *
	 * @param detectors Map of label/regex filter
	 */
	public void setDetectors(Map<String, Predicate<String>> detectors) {
		this.detectors = detectors;
	}

	private List<Path> detectorFilteredFileList(String detector) {
		Predicate<String> filter = detectors.get(detector);
		List<Path> dataFileCollectedForDetector = Collections.synchronizedList(new ArrayList<Path>());
		for (Path file : dirWatcher.getDataFilesCollected()) {
			if (filter.test(file.getFileName().toString())) {
				dataFileCollectedForDetector.add(file);
			}
		}
		return dataFileCollectedForDetector;
	}

}