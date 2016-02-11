package rocks.inspectit.agent.java.connection.impl;

import java.util.Map;

import rocks.inspectit.agent.java.config.impl.MethodSensorTypeConfig;
import rocks.inspectit.agent.java.connection.AbstractRemoteMethodCall;
import rocks.inspectit.shared.all.cmr.service.IRegistrationService;

/**
 * Class which encapsulates the request to the remote object {@link IRegistrationService}. The
 * method to call is {@link IRegistrationService#registerMethodSensorTypeIdent(long, String, Map)}
 * 
 * @author Patrice Bouillet
 * 
 */
public class RegisterMethodSensorType extends AbstractRemoteMethodCall<IRegistrationService, Long> {

	/**
	 * The method sensor type configuration which is registered at the server.
	 */
	private final MethodSensorTypeConfig methodSensorTypeConfig;

	/**
	 * The ID of the current platform used for the registering process.
	 */
	private final long platformId;

	/**
	 * The only constructor for this class accepts three attributes.
	 * 
	 * @param registrationService
	 *            The remote object.
	 * @param methodSensorTypeConfig
	 *            The {@link MethodSensorTypeConfig} which is registered at the server.
	 * @param platformId
	 *            The ID of the platform.
	 */
	public RegisterMethodSensorType(IRegistrationService registrationService, MethodSensorTypeConfig methodSensorTypeConfig, long platformId) {
		super(registrationService);
		this.methodSensorTypeConfig = methodSensorTypeConfig;
		this.platformId = platformId;
	}

	/**
	 * {@inheritDoc}
	 */
	protected Long performRemoteCall(IRegistrationService remoteObject) {
		return Long.valueOf(remoteObject.registerMethodSensorTypeIdent(platformId, methodSensorTypeConfig.getClassName(), methodSensorTypeConfig.getParameters()));
	}
}