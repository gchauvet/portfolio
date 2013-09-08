package name.abuchen.portfolio.ui.handlers;

import javax.inject.Named;

import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.UIConstants;

import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.model.application.ui.basic.MPartStack;
import org.eclipse.e4.ui.services.IServiceConstants;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.eclipse.e4.ui.workbench.modeling.EPartService;
import org.eclipse.e4.ui.workbench.modeling.EPartService.PartState;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

public class OpenFileHandler
{
    @Execute
    public void execute(@Named(IServiceConstants.ACTIVE_SHELL) Shell shell, MApplication app, EPartService partService,
                    EModelService modelService)
    {
        FileDialog dialog = new FileDialog(shell, SWT.OPEN);
        dialog.setFilterExtensions(new String[] { "*.xml" }); //$NON-NLS-1$
        dialog.setFilterNames(new String[] { Messages.LabelPortfolioPerformanceFile });
        String fileSelected = dialog.open();

        if (fileSelected != null)
        {
            MPart part = partService.createPart(UIConstants.PORTFOLIO_PART);
            part.setLabel(fileSelected);
            part.getPersistedState().put("file", fileSelected);

            MPartStack stack = (MPartStack) modelService.find(UIConstants.MAIN_PARTSTACK, app);
            stack.getChildren().add(part);

            partService.showPart(part, PartState.ACTIVATE);
        }
    }
}
