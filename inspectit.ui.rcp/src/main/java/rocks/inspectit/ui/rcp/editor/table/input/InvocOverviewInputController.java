package rocks.inspectit.ui.rcp.editor.table.input;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IBaseLabelProvider;
import org.eclipse.jface.viewers.IContentProvider;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.springframework.http.HttpStatus;

import rocks.inspectit.shared.all.cmr.model.MethodIdent;
import rocks.inspectit.shared.all.cmr.service.ICachedDataService;
import rocks.inspectit.shared.all.communication.data.HttpTimerData;
import rocks.inspectit.shared.all.communication.data.HttpTimerDataHelper;
import rocks.inspectit.shared.all.communication.data.InvocationSequenceData;
import rocks.inspectit.shared.all.communication.data.cmr.ApplicationData;
import rocks.inspectit.shared.all.communication.data.cmr.BusinessTransactionData;
import rocks.inspectit.shared.cs.cmr.service.IInvocationDataAccessService;
import rocks.inspectit.shared.cs.cmr.service.ISpanService;
import rocks.inspectit.shared.cs.communication.comparator.DefaultDataComparatorEnum;
import rocks.inspectit.shared.cs.communication.comparator.IDataComparator;
import rocks.inspectit.shared.cs.communication.comparator.InvocationSequenceDataComparatorEnum;
import rocks.inspectit.shared.cs.communication.comparator.MethodSensorDataComparatorEnum;
import rocks.inspectit.shared.cs.communication.comparator.ResultComparator;
import rocks.inspectit.shared.cs.communication.data.InvocationSequenceDataHelper;
import rocks.inspectit.shared.cs.data.invocationtree.InvocationTreeBuilder;
import rocks.inspectit.shared.cs.data.invocationtree.InvocationTreeBuilder.Mode;
import rocks.inspectit.shared.cs.data.invocationtree.InvocationTreeElement;
import rocks.inspectit.ui.rcp.InspectIT;
import rocks.inspectit.ui.rcp.InspectITImages;
import rocks.inspectit.ui.rcp.editor.inputdefinition.InputDefinition;
import rocks.inspectit.ui.rcp.editor.inputdefinition.InputDefinition.IdDefinition;
import rocks.inspectit.ui.rcp.editor.preferences.PreferenceEventCallback.PreferenceEvent;
import rocks.inspectit.ui.rcp.editor.preferences.PreferenceId;
import rocks.inspectit.ui.rcp.editor.preferences.PreferenceId.LiveMode;
import rocks.inspectit.ui.rcp.editor.root.IRootEditor;
import rocks.inspectit.ui.rcp.editor.table.RemoteTableViewerComparator;
import rocks.inspectit.ui.rcp.editor.tooltip.IColumnToolTipProvider;
import rocks.inspectit.ui.rcp.editor.viewers.StyledCellIndexLabelProvider;
import rocks.inspectit.ui.rcp.formatter.ImageFormatter;
import rocks.inspectit.ui.rcp.formatter.NumberFormatter;
import rocks.inspectit.ui.rcp.formatter.TextFormatter;
import rocks.inspectit.ui.rcp.preferences.PreferencesConstants;
import rocks.inspectit.ui.rcp.preferences.PreferencesUtils;
import rocks.inspectit.ui.rcp.repository.CmrRepositoryDefinition;

/**
 * This input controller displays an overview of {@link InvocationSequenceData} objects.
 *
 * @author Patrice Bouillet
 *
 */
public class InvocOverviewInputController extends AbstractTableInputController {

	/**
	 * The ID of this subview / controller.
	 */
	public static final String ID = "inspectit.subview.table.invocoverview";

	/**
	 * The private inner enumeration used to define the used IDs which are mapped into the columns.
	 * The order in this enumeration represents the order of the columns. If it is reordered,
	 * nothing else has to be changed.
	 *
	 * @author Patrice Bouillet
	 *
	 */
	protected enum Column {
		/** The time column. */
		NESTED_DATA("Nested Data", 40, null, InvocationSequenceDataComparatorEnum.NESTED_DATA),
		/** The time column. */
		TIME("Start Time", 150, InspectITImages.IMG_TIMESTAMP, DefaultDataComparatorEnum.TIMESTAMP),
		/** Response status. */
		RESPONSE_STATUS("Status", 50, null, InvocationSequenceDataComparatorEnum.RESPONSE_CODE),
		/** The method column. */
		METHOD("Method", 550, InspectITImages.IMG_METHOD, MethodSensorDataComparatorEnum.METHOD),
		/** The duration column. */
		DURATION("Duration (ms)", 100, InspectITImages.IMG_TIME, InvocationSequenceDataComparatorEnum.DURATION),
		/** The count column. */
		COUNT("Child Count", 100, null, InvocationSequenceDataComparatorEnum.CHILD_COUNT),
		/** The URL column. */
		URL("URL", 150, null, InvocationSequenceDataComparatorEnum.URL),
		/** The application column. */
		APPLICATION("Application", 150, null, InvocationSequenceDataComparatorEnum.APPLICATION),
		/** The business transaction column. */
		BUSINESS_TRANSACTION("Business Transaction", 150, null, InvocationSequenceDataComparatorEnum.BUSINESS_TRANSACTION),
		/** The Use case column. */
		USE_CASE("Use case", 100, null, InvocationSequenceDataComparatorEnum.USE_CASE);

		/** The name. */
		private final String name;
		/** The width of the column. */
		private final int width;
		/** The image descriptor. Can be <code>null</code> */
		private final Image image;
		/** Comparator for the column. */
		protected IDataComparator<? super InvocationSequenceData> dataComparator;

		/**
		 * Default constructor which creates a column enumeration object.
		 *
		 * @param name
		 *            The name of the column.
		 * @param width
		 *            The width of the column.
		 * @param imageName
		 *            The name of the image. Names are defined in {@link InspectITImages}.
		 * @param dataComparator
		 *            Comparator for the column.
		 */
		Column(String name, int width, String imageName, IDataComparator<? super InvocationSequenceData> dataComparator) {
			this.name = name;
			this.width = width;
			this.image = InspectIT.getDefault().getImage(imageName);
			this.dataComparator = dataComparator;
		}

		/**
		 * Converts an ordinal into a column.
		 *
		 * @param i
		 *            The ordinal.
		 * @return The appropriate column.
		 */
		public static Column fromOrd(int i) {
			if ((i < 0) || (i >= Column.values().length)) {
				throw new IndexOutOfBoundsException("Invalid ordinal");
			}
			return Column.values()[i];
		}

	}

	/**
	 * Default comparator when no sorting is defined.
	 */
	private final ResultComparator<InvocationSequenceData> defaultComparator = new ResultComparator<>(DefaultDataComparatorEnum.TIMESTAMP, false);

	/**
	 * The template object which is send to the server.
	 */
	private InvocationSequenceData template;

	/**
	 * The list of invocation sequence data objects which is displayed.
	 */
	private List<InvocationSequenceData> invocationSequenceData = new ArrayList<>();

	/**
	 * The limit of the result set.
	 */
	private int limit = PreferencesUtils.getIntValue(PreferencesConstants.ITEMS_COUNT_TO_SHOW);

	/**
	 * The used data access service to access the data on the CMR.
	 */
	private IInvocationDataAccessService dataAccessService;

	/**
	 * The cached service is needed because of the ID mappings.
	 */
	private ICachedDataService cachedDataService;

	/**
	 * Date to display invocations from.
	 */
	private Date fromDate = null;

	/**
	 * Date to display invocations to.
	 */
	private Date toDate = null;

	/**
	 * Are we in live mode.
	 */
	private boolean autoUpdate = LiveMode.ACTIVE_DEFAULT;

	/**
	 * Empty styled string.
	 */
	private final StyledString emptyStyledString = new StyledString();

	/**
	 * The resource manager is used for the images etc.
	 */
	private final LocalResourceManager resourceManager = new LocalResourceManager(JFaceResources.getResources());

	/**
	 * Result comparator to be used on the server.
	 */
	private ResultComparator<InvocationSequenceData> resultComparator = defaultComparator;

	/**
	 * The span service.
	 */
	private ISpanService spanService;

	/**
	 *
	 * @return Returns list of invocation sequence data that represents a table input.
	 */
	protected List<InvocationSequenceData> getInvocationSequenceData() {
		return invocationSequenceData;
	}

	/**
	 * Returns data access service for retrieving the data from the server.
	 *
	 * @return Returns data access service.
	 */
	protected IInvocationDataAccessService getDataAccessService() {
		return dataAccessService;
	}

	/**
	 * Gets {@link #cachedDataService}.
	 *
	 * @return {@link #cachedDataService}
	 */
	protected ICachedDataService getCachedDataService() {
		return cachedDataService;
	}

	/**
	 * Returns current view item count limit defined for the view.
	 *
	 * @return Returns current view item count limit.
	 */
	protected int getLimit() {
		return limit;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setInputDefinition(InputDefinition inputDefinition) {
		super.setInputDefinition(inputDefinition);

		template = new InvocationSequenceData();
		template.setPlatformIdent(inputDefinition.getIdDefinition().getPlatformId());
		template.setSensorTypeIdent(inputDefinition.getIdDefinition().getSensorTypeId());
		template.setMethodIdent(inputDefinition.getIdDefinition().getMethodId());

		dataAccessService = inputDefinition.getRepositoryDefinition().getInvocationDataAccessService();
		cachedDataService = inputDefinition.getRepositoryDefinition().getCachedDataService();
		spanService = inputDefinition.getRepositoryDefinition().getSpanService();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void createColumns(TableViewer tableViewer) {
		for (Column column : Column.values()) {
			TableViewerColumn viewerColumn = new TableViewerColumn(tableViewer, SWT.NONE);
			viewerColumn.getColumn().setMoveable(true);
			viewerColumn.getColumn().setResizable(true);
			viewerColumn.getColumn().setText(column.name);
			viewerColumn.getColumn().setWidth(column.width);
			if (null != column.image) {
				viewerColumn.getColumn().setImage(column.image);
			}
			mapTableViewerColumn(column, viewerColumn);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Object getTableInput() {
		// this list will be filled with data
		return invocationSequenceData;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IContentProvider getContentProvider() {
		return new InvocOverviewContentProvider();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public IBaseLabelProvider getLabelProvider() {
		return new InvocOverviewLabelProvider();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ViewerComparator getComparator() {
		RemoteTableViewerComparator<InvocationSequenceData> invocOverviewViewerComparator = new RemoteTableViewerComparator<InvocationSequenceData>() {
			@Override
			protected void sortRemotely(ResultComparator<InvocationSequenceData> resultComparator) {
				if (null != resultComparator) {
					InvocOverviewInputController.this.resultComparator = resultComparator;
				} else {
					InvocOverviewInputController.this.resultComparator = defaultComparator;
				}
				loadDataFromService();
			}
		};
		for (Column column : Column.values()) {
			// since it is remote sorting we do not provide local cached data service
			ResultComparator<InvocationSequenceData> resultComparator = new ResultComparator<>(column.dataComparator);
			invocOverviewViewerComparator.addColumn(getMappedTableViewerColumn(column).getColumn(), resultComparator);
		}

		return invocOverviewViewerComparator;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Set<PreferenceId> getPreferenceIds() {
		Set<PreferenceId> preferences = EnumSet.noneOf(PreferenceId.class);
		if (getInputDefinition().getRepositoryDefinition() instanceof CmrRepositoryDefinition) {
			preferences.add(PreferenceId.CLEAR_BUFFER);
			preferences.add(PreferenceId.LIVEMODE);
		}
		preferences.add(PreferenceId.UPDATE);
		preferences.add(PreferenceId.ITEMCOUNT);
		preferences.add(PreferenceId.TIMELINE);
		return preferences;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void preferenceEventFired(PreferenceEvent preferenceEvent) {
		switch (preferenceEvent.getPreferenceId()) {
		case TIMELINE:
			if (preferenceEvent.getPreferenceMap().containsKey(PreferenceId.TimeLine.FROM_DATE_ID)) {
				fromDate = (Date) preferenceEvent.getPreferenceMap().get(PreferenceId.TimeLine.FROM_DATE_ID);
			}
			if (preferenceEvent.getPreferenceMap().containsKey(PreferenceId.TimeLine.TO_DATE_ID)) {
				toDate = (Date) preferenceEvent.getPreferenceMap().get(PreferenceId.TimeLine.TO_DATE_ID);
			}
			break;
		case LIVEMODE:
			if (preferenceEvent.getPreferenceMap().containsKey(PreferenceId.LiveMode.BUTTON_LIVE_ID)) {
				autoUpdate = (Boolean) preferenceEvent.getPreferenceMap().get(PreferenceId.LiveMode.BUTTON_LIVE_ID);
			}
			break;
		default:
			break;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean canOpenInput(List<? extends Object> data) {
		if (data.isEmpty()) {
			return true;
		}
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setLimit(int limit) {
		this.limit = limit;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void doRefresh(IProgressMonitor monitor, IRootEditor rootEditor) {
		monitor.beginTask("Updating Invocation Overview", IProgressMonitor.UNKNOWN);
		monitor.subTask("Retrieving the Invocation Overview");

		loadDataFromService();

		monitor.done();
	}

	/**
	 * Reloads the data from the service.
	 */
	private void loadDataFromService() {
		List<InvocationSequenceData> invocData;

		if (!autoUpdate) {
			if (template.getMethodIdent() != IdDefinition.ID_NOT_USED) {
				invocData = dataAccessService.getInvocationSequenceOverview(template.getPlatformIdent(), template.getMethodIdent(), limit, fromDate, toDate, resultComparator);
			} else {
				invocData = dataAccessService.getInvocationSequenceOverview(template.getPlatformIdent(), limit, fromDate, toDate, resultComparator);
			}
		} else {
			if (template.getMethodIdent() != IdDefinition.ID_NOT_USED) {
				invocData = dataAccessService.getInvocationSequenceOverview(template.getPlatformIdent(), template.getMethodIdent(), limit, resultComparator);
			} else {
				invocData = dataAccessService.getInvocationSequenceOverview(template.getPlatformIdent(), limit, resultComparator);
			}
		}

		// why this? so only update with new data if returned collection is not empty, i would say
		// with every update, if it is empty, then there is nothing to display
		// then i also done need the clearInvocationFlag
		// I changed here, .clear() is now out of if clause

		invocationSequenceData.clear();
		if (!invocData.isEmpty()) {
			invocationSequenceData.addAll(invocData);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void doubleClick(DoubleClickEvent event) {
		final StructuredSelection selection = (StructuredSelection) event.getSelection();
		if (!selection.isEmpty()) {
			try {
				PlatformUI.getWorkbench().getProgressService().busyCursorWhile(new IRunnableWithProgress() {
					@Override
					public void run(final IProgressMonitor monitor) {
						monitor.beginTask("Retrieving Invocation detail data", IProgressMonitor.UNKNOWN);

						InvocationSequenceData invocationSequenceData = (InvocationSequenceData) selection.getFirstElement();
						InvocationSequenceData data = dataAccessService.getInvocationSequenceDetail(invocationSequenceData);

						final InvocationTreeElement tree = new InvocationTreeBuilder().setSpanService(spanService).setInvocationSequence(data).setMode(Mode.SINGLE).build();

						Display.getDefault().asyncExec(new Runnable() {
							@Override
							public void run() {
								IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
								IWorkbenchPage page = window.getActivePage();
								IRootEditor rootEditor = (IRootEditor) page.getActiveEditor();
								rootEditor.setDataInput(Collections.singletonList(tree));
							}
						});
						monitor.done();
					}
				});
			} catch (InvocationTargetException e) {
				MessageDialog.openError(Display.getDefault().getActiveShell().getShell(), "Error", e.getCause().toString());
			} catch (InterruptedException e) {
				MessageDialog.openInformation(Display.getDefault().getActiveShell().getShell(), "Cancelled", e.getCause().toString());
			}
		}
	}

	/**
	 * The label provider for this view.
	 *
	 * @author Patrice Bouillet
	 *
	 */
	private final class InvocOverviewLabelProvider extends StyledCellIndexLabelProvider implements IColumnToolTipProvider {

		/**
		 * {@inheritDoc}
		 */
		@Override
		protected StyledString getStyledText(Object element, int index) {
			InvocationSequenceData data = (InvocationSequenceData) element;
			MethodIdent methodIdent = cachedDataService.getMethodIdentForId(data.getMethodIdent());
			Column enumId = Column.fromOrd(index);

			return getStyledTextForColumn(data, methodIdent, enumId);
		}

		/**
		 *
		 * {@inheritDoc}
		 */
		@Override
		protected Image getColumnImage(Object element, int index) {
			InvocationSequenceData data = (InvocationSequenceData) element;
			Column enumId = Column.fromOrd(index);

			switch (enumId) {
			case NESTED_DATA:
				if (InvocationSequenceDataHelper.hasNestedSqlStatements(data) && InvocationSequenceDataHelper.hasNestedExceptions(data)) {
					return ImageFormatter.getCombinedImage(resourceManager, SWT.HORIZONTAL, InspectIT.getDefault().getImageDescriptor(InspectITImages.IMG_DATABASE),
							InspectIT.getDefault().getImageDescriptor(InspectITImages.IMG_EXCEPTION_SENSOR));
				} else if (InvocationSequenceDataHelper.hasNestedSqlStatements(data)) {
					return InspectIT.getDefault().getImage(InspectITImages.IMG_DATABASE);
				} else if (InvocationSequenceDataHelper.hasNestedExceptions(data)) {
					return InspectIT.getDefault().getImage(InspectITImages.IMG_EXCEPTION_SENSOR);
				} else {
					return super.getColumnImage(element, index);
				}
			case RESPONSE_STATUS:
				if (InvocationSequenceDataHelper.hasHttpTimerData(data)) {
					return ImageFormatter.getResponseStatusImage(((HttpTimerData) data.getTimerData()).getHttpResponseStatus());
				} else {
					return super.getColumnImage(element, index);
				}
			default:
				return super.getColumnImage(element, index);
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String getToolTipText(Object element, int index) {
			InvocationSequenceData data = (InvocationSequenceData) element;
			Column enumId = Column.fromOrd(index);
			switch (enumId) {
			case NESTED_DATA:
				if (InvocationSequenceDataHelper.hasNestedSqlStatements(data) || InvocationSequenceDataHelper.hasNestedExceptions(data)) {
					StringBuilder toolTip = new StringBuilder("This invocation contains:");
					if (InvocationSequenceDataHelper.hasNestedSqlStatements(data)) {
						toolTip.append("\n - SQL statement(s)");
					}
					if (InvocationSequenceDataHelper.hasNestedExceptions(data)) {
						toolTip.append("\n - Exception(s)");
					}
					return toolTip.toString();
				} else {
					return super.getToolTipText(element, index);
				}
			case RESPONSE_STATUS:
				if (InvocationSequenceDataHelper.hasHttpTimerData(data)) {
					if (HttpTimerDataHelper.hasResponseCode((HttpTimerData) data.getTimerData())) {
						try {
							HttpStatus httpStatus = HttpStatus.valueOf(((HttpTimerData) data.getTimerData()).getHttpResponseStatus());
							return httpStatus.getReasonPhrase();
						} catch (IllegalArgumentException e) {
							// non standard response code
							return "Non-standard Response Code";
						}
					} else {
						return "Response Status Unavailable";
					}
				} else {
					return null;
				}
			default:
				return null;
			}
		}
	}

	/**
	 * The content provider for this view.
	 *
	 * @author Patrice Bouillet
	 *
	 */
	private static final class InvocOverviewContentProvider implements IStructuredContentProvider {

		/**
		 * {@inheritDoc}
		 */
		@Override
		@SuppressWarnings("unchecked")
		public Object[] getElements(Object inputElement) {
			List<InvocationSequenceData> invocationSequenceData = (List<InvocationSequenceData>) inputElement;
			return invocationSequenceData.toArray();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void dispose() {
		}

	}

	/**
	 * Returns the styled text for a specific column.
	 *
	 * @param data
	 *            The data object to extract the information from.
	 * @param methodIdent
	 *            The method ident object.
	 * @param enumId
	 *            The enumeration ID.
	 * @return The styled string containing the information from the data object.
	 */
	private StyledString getStyledTextForColumn(InvocationSequenceData data, MethodIdent methodIdent, Column enumId) {
		switch (enumId) {
		case NESTED_DATA:
			return emptyStyledString;
		case RESPONSE_STATUS:
			if (InvocationSequenceDataHelper.hasHttpTimerData(data)) {
				if (HttpTimerDataHelper.hasResponseCode((HttpTimerData) data.getTimerData())) {
					return new StyledString(String.valueOf(((HttpTimerData) data.getTimerData()).getHttpResponseStatus()));
				} else {
					return new StyledString("N/A");
				}

			} else {
				return emptyStyledString;
			}
		case TIME:
			return new StyledString(NumberFormatter.formatTimeWithMillis(data.getTimeStamp()));
		case METHOD:
			return TextFormatter.getStyledMethodString(methodIdent);
		case DURATION:
			if (InvocationSequenceDataHelper.hasTimerData(data)) {
				return new StyledString(NumberFormatter.formatDouble(data.getTimerData().getDuration()));
			} else {
				// this duration is always available but could differ from
				// the timer data duration as these measures are taken
				// separately.
				return new StyledString(NumberFormatter.formatDouble(data.getDuration()));
			}
		case COUNT:
			return new StyledString(NumberFormatter.formatLong(data.getChildCount()));
		case URL:
			if (InvocationSequenceDataHelper.hasHttpTimerData(data)) {
				String url = ((HttpTimerData) data.getTimerData()).getHttpInfo().getUrl();
				if (null != url) {
					return new StyledString(url);
				} else {
					return emptyStyledString;
				}
			} else {
				return emptyStyledString;
			}
		case APPLICATION:
			ApplicationData appData = cachedDataService.getApplicationForId(data.getApplicationId());
			if (null != appData) {
				return new StyledString(appData.getName());
			} else {
				return emptyStyledString;
			}
		case BUSINESS_TRANSACTION:
			BusinessTransactionData btData = cachedDataService.getBusinessTransactionForId(data.getApplicationId(), data.getBusinessTransactionId());
			if (null != btData) {
				return new StyledString(btData.getName());
			} else {
				return emptyStyledString;
			}
		case USE_CASE:
			if (InvocationSequenceDataHelper.hasHttpTimerData(data)) {
				String useCase = ((HttpTimerData) data.getTimerData()).getHttpInfo().getInspectItTaggingHeaderValue();
				if (null != useCase) {
					return new StyledString(useCase);
				} else {
					return emptyStyledString;
				}
			} else {
				return emptyStyledString;
			}
		default:
			return new StyledString("error");
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getReadableString(Object object) {
		if (object instanceof InvocationSequenceData) {
			InvocationSequenceData data = (InvocationSequenceData) object;
			StringBuilder sb = new StringBuilder();
			MethodIdent methodIdent = cachedDataService.getMethodIdentForId(data.getMethodIdent());
			for (Column column : Column.values()) {
				sb.append(getStyledTextForColumn(data, methodIdent, column).toString());
				sb.append('\t');
			}
			return sb.toString();
		}
		throw new RuntimeException("Could not create the human readable string!");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<String> getColumnValues(Object object) {
		if (object instanceof InvocationSequenceData) {
			InvocationSequenceData data = (InvocationSequenceData) object;
			MethodIdent methodIdent = cachedDataService.getMethodIdentForId(data.getMethodIdent());
			List<String> values = new ArrayList<>();
			for (Column column : Column.values()) {
				values.add(getStyledTextForColumn(data, methodIdent, column).toString());
			}
			return values;
		}
		throw new RuntimeException("Could not create the column values!");
	}

	/**
	 * Gets {@link #resultComparator}.
	 *
	 * @return {@link #resultComparator}
	 */
	public ResultComparator<InvocationSequenceData> getResultComparator() {
		return resultComparator;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void dispose() {
		resourceManager.dispose();
	}
}
