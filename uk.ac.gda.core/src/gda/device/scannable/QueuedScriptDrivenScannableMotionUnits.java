/*-
 * Copyright © 2011 Diamond Light Source Ltd.
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

package gda.device.scannable;

import gda.commandqueue.JythonCommandCommandProvider;
import gda.commandqueue.Queue;
import gda.device.DeviceException;
import gda.device.ScannableMotionUnits;
import gda.factory.FactoryException;
import gda.observable.IObserver;
import gda.observable.ObservableComponent;

import org.jscience.physics.quantities.Quantity;
import org.springframework.beans.factory.InitializingBean;

/**
 * Class to wrap a ScannableMotionUnits that is to be driven by a script that is run on the queue
 * The command to be executed is generated by the code:
 * String.format(commandFormat, (Object []) ScannableUtils.objectToArray(position));
 * This allows the position to have multiple parts providing the script command can accept them
 *
 * moveTo uses the evaluateCommand method of the ICommandRunner. If the script returns a string of length>0 then a DeviceException is raised
 * Note moveTo is blocking so should not be called from the GUI thread

 * asynchronousMoveTo uses the runCommand method of the ICommandRunner.
 * No indication is given to the caller if the script fails.
 *
 * Note that runCommand in jython server always spawns a new thread to run the command so the source scannnable may not be
 * busy as soon as runCommand is started. In fact as the script may be doing various other things before trying to move the
 * scannable motor this will not be that case.
 *
 *
 * e.g.
 *
 * scannableUnderTest.setCommandFormat("myscript(%5.5g)");
 * scannableUnderTest.setSource(scannableMotor);
 * scannableUnderTest.setCommandRunner(InterfaceProvider.getCommandRunner())
 * scannableUnderTest.afterPropertiesSet();
 *
 * where
 * def myscript(new_position):
 * 		...
 * 		scannableMotor.asynchronousMoveTo(newPos);
 * 		...
 *
 */
public class QueuedScriptDrivenScannableMotionUnits implements ScannableMotionUnits, InitializingBean {
	private ScannableMotionUnits scannable;
	private String commandFormat;
	private Queue queue;

	ObservableComponent obsComp = new ObservableComponent();
	private IObserver observer;


	public ScannableMotionUnits getScannable() {
		return scannable;
	}

	public void setScannable(ScannableMotionUnits scannable) {
		this.scannable = scannable;
	}

	public String getCommandFormat() {
		return commandFormat;
	}

	public void setCommandFormat(String commandFormat) {
		this.commandFormat = commandFormat;
	}




	public Queue getQueue() {
		return queue;
	}

	public void setQueue(Queue queue) {
		this.queue = queue;
	}

	public QueuedScriptDrivenScannableMotionUnits() {
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public void reconfigure() throws FactoryException {
		scannable.reconfigure();
	}

	@Override
	public Object getPosition() throws DeviceException {
		return scannable.getPosition();
	}

	String name;
	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setAttribute(String attributeName, Object value) throws DeviceException {
		scannable.setAttribute(attributeName, value);
	}

	@Override
	public String toString() {
		return scannable.toString();
	}


	@Override
	public String getUserUnits() {
		return scannable.getUserUnits();
	}

	@Override
	public String checkPositionWithinGdaLimits(Double[] externalPosition) {
		return scannable.checkPositionWithinGdaLimits(externalPosition);
	}


	@Override
	public void setUserUnits(String userUnitsString) throws DeviceException {
		scannable.setUserUnits(userUnitsString);
	}

	@Override
	public void moveTo(Object position) throws DeviceException {
		String commandToRun = String.format(commandFormat, (Object []) ScannableUtils.objectToArray(position));
		try {
			queue.addToTail(new JythonCommandCommandProvider(commandToRun, commandToRun, null));
		} catch (Exception e) {
			if( e instanceof DeviceException)
				throw (DeviceException)e;
			throw new DeviceException("Error issuing command " + commandToRun, e);
		}

	}

	@Override
	public Object getAttribute(String attributeName) throws DeviceException {
		return scannable.getAttribute(attributeName);
	}

	@Override
	public void asynchronousMoveTo(Object position) throws DeviceException {
		moveTo(position);
	}

	@Override
	public String checkPositionWithinGdaLimits(Object externalPosition) {
		return scannable.checkPositionWithinGdaLimits(externalPosition);
	}

	@Override
	public void close() throws DeviceException {
		scannable.close();
	}

	@Override
	public void setProtectionLevel(int newLevel) throws DeviceException {
		scannable.setProtectionLevel(newLevel);
	}

	@Override
	public String getHardwareUnitString() {
		return scannable.getHardwareUnitString();
	}

	@Override
	public void setLowerGdaLimits(Double[] externalLowerLim) throws Exception {
		scannable.setLowerGdaLimits(externalLowerLim);
	}

	@Override
	public void setHardwareUnitString(String hardwareUnitString) throws DeviceException {
		scannable.setHardwareUnitString(hardwareUnitString);
	}

	@Override
	public int getProtectionLevel() throws DeviceException {
		return scannable.getProtectionLevel();
	}

	@Override
	public void setLowerGdaLimits(Double externalLowerLim) throws Exception {
		scannable.setLowerGdaLimits(externalLowerLim);
	}

	@Override
	public void stop() throws DeviceException {
		scannable.stop();
	}

	@Override
	public boolean isBusy() throws DeviceException {
		return scannable.isBusy();
	}

	@Override
	public Double[] getLowerGdaLimits() {
		return scannable.getLowerGdaLimits();
	}

	@Override
	public String[] getAcceptableUnits() {
		return scannable.getAcceptableUnits();
	}

	@Override
	public void addAcceptableUnit(String newUnit) throws DeviceException {
		scannable.addAcceptableUnit(newUnit);
	}

	@Override
	public void waitWhileBusy() throws DeviceException, InterruptedException {
		scannable.waitWhileBusy();
	}

	@Override
	public void setUpperGdaLimits(Double[] externalUpperLim) throws Exception {
		scannable.setUpperGdaLimits(externalUpperLim);
	}

	@Override
	public Quantity[] getPositionAsQuantityArray() throws DeviceException {
		return scannable.getPositionAsQuantityArray();
	}

	@Override
	public boolean isAt(Object positionToTest) throws DeviceException {
		return scannable.isAt(positionToTest);
	}

	@Override
	public void setOffset(Object offsetPositionInExternalUnits) {
		scannable.setOffset(offsetPositionInExternalUnits);
	}

	@Override
	public void setUpperGdaLimits(Double externalUpperLim) throws Exception {
		scannable.setUpperGdaLimits(externalUpperLim);
	}

	@Override
	public void setLevel(int level) {
		scannable.setLevel(level);
	}

	@Override
	public int getLevel() {
		return scannable.getLevel();
	}

	@Override
	public Double[] getUpperGdaLimits() {
		return scannable.getUpperGdaLimits();
	}

	@Override
	public String[] getInputNames() {
		return scannable.getInputNames();
	}

	@Override
	public String checkPositionValid(Object position) throws DeviceException {
		return scannable.checkPositionValid(position);
	}

	@Override
	public void setInputNames(String[] names) {
		scannable.setInputNames(names);
	}

	@Override
	public String[] getExtraNames() {
		return scannable.getExtraNames();
	}

	@Override
	public void setExtraNames(String[] names) {
		scannable.setExtraNames(names);
	}

	@Override
	public Double[] getTolerances() throws DeviceException {
		return scannable.getTolerances();
	}

	@Override
	public void setOutputFormat(String[] names) {
		scannable.setOutputFormat(names);
	}

	@Override
	public String[] getOutputFormat() {
		return scannable.getOutputFormat();
	}

	@Override
	public void setTolerance(Double tolerence) throws DeviceException {
		scannable.setTolerance(tolerence);
	}

	@SuppressWarnings("deprecation")
	@Override
	public void atStart() throws DeviceException {
		scannable.atStart();
	}

	@Override
	public void setTolerances(Double[] tolerence) throws DeviceException {
		scannable.setTolerances(tolerence);
	}

	@SuppressWarnings("deprecation")
	@Override
	public void atEnd() throws DeviceException {
		scannable.atEnd();
	}

	@Override
	public int getNumberTries() {
		return scannable.getNumberTries();
	}

	@Override
	public void atScanStart() throws DeviceException {
		scannable.atScanStart();
	}

	@Override
	public void atScanEnd() throws DeviceException {
		scannable.atScanEnd();
	}

	@Override
	public void setNumberTries(int numberTries) {
		scannable.setNumberTries(numberTries);
	}

	@Override
	public void a(Object position) throws DeviceException {
		scannable.a(position);
	}

	@Override
	public void atScanLineStart() throws DeviceException {
		scannable.atScanLineStart();
	}

	@Override
	public void ar(Object position) throws DeviceException {
		scannable.ar(position);
	}

	@Override
	public void r(Object position) throws DeviceException {
		scannable.r(position);
	}

	@Override
	public void setOffset(Double... offsetArray) {
		scannable.setOffset(offsetArray);
	}

	@Override
	public void atScanLineEnd() throws DeviceException {
		scannable.atScanLineEnd();
	}
	@Override
	public void atPointStart() throws DeviceException {
		scannable.atPointStart();
	}

	@Override
	public void setScalingFactor(Double... scaleArray) {
		scannable.setScalingFactor(scaleArray);
	}

	@Override
	public void atPointEnd() throws DeviceException {
		scannable.atPointEnd();
	}

	@Override
	public Double[] getOffset() {
		return scannable.getOffset();
	}

	@Override
	public void atLevelMoveStart() throws DeviceException {
		scannable.atLevelMoveStart();
	}

	@Override
	public void atLevelStart() throws DeviceException {
		scannable.atLevelStart();
	}

	@Override
	public void atLevelEnd() throws DeviceException {
		scannable.atLevelEnd();
	}

	@Override
	public Double[] getScalingFactor() {
		return scannable.getScalingFactor();
	}

	@Override
	public void atCommandFailure() throws DeviceException {
		scannable.atCommandFailure();
	}

	@Override
	public String toFormattedString() {
		return scannable.toFormattedString();
	}



	@Override
	public void addIObserver(IObserver anIObserver) {
		obsComp.addIObserver(anIObserver);
	}

	@Override
	public void deleteIObserver(IObserver anIObserver) {
		obsComp.deleteIObserver(anIObserver);
	}

	@Override
	public void deleteIObservers() {
		obsComp.deleteIObservers();
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		if( scannable == null){
			throw new IllegalArgumentException("Source is null");
		}
		if( queue == null){
			throw new IllegalArgumentException("queue is null");
		}
		if( commandFormat == null){
			throw new IllegalArgumentException("format is null");
		}
		observer = new IObserver() {

			@Override
			public void update(Object source, Object arg) {
				obsComp.notifyIObservers(QueuedScriptDrivenScannableMotionUnits.this, arg);

			}
		};
		scannable.addIObserver(observer);

	}

	void dispose(){
		if(scannable != null && observer != null ){
			scannable.deleteIObserver(observer);
		}
	}

}