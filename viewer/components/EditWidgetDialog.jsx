import React from 'react';
import PropTypes from 'prop-types';
import Promise from 'promise';
import LayoutHandler from '../util/layouthandler.js';
import OverlayDialog from './OverlayDialog.jsx';
import DialogContainer from './OverlayDialogDisplay.jsx';
import WidgetFactory,{WidgetParameter} from '../components/WidgetFactory.jsx';
import assign from 'object-assign';
import {Input,Checkbox,InputReadOnly,ColorSelector} from './Inputs.jsx';
import ColorDialog from './ColorDialog.jsx';


class EditWidgetDialog extends React.Component{
    constructor(props){
        super(props);
        this.state= {
            widget:props.current,
            sizeCount:0,
            parameters:WidgetFactory.getEditableWidgetParameters(props.current)};
        this.selectWidget=this.selectWidget.bind(this);
        this.insert=this.insert.bind(this);
        this.sizeCount=0;
    }
    insert(before){
        if (! this.props.insertCallback) return;
        this.props.closeCallback();
        this.props.insertCallback(this.state.widget,before);
    }

    showSelection(title,list,callback){
        let self=this;
        let idx=0;
        let displayList=[];
        list.forEach((el)=>{
            let item=undefined;
            if (typeof(el) === 'object') {
                item=assign({},el);
            }
            else{
                item={name:el}
            }
            item.key=idx;
            if (! item.label) item.label=item.name;
            idx++;
            displayList.push(item);
        });
        displayList.sort((a,b)=>{
            if ( ! a || ! a.name) return -1;
            if (! b || ! b.name) return 1;
            let na=a.name.toUpperCase();
            let nb=b.name.toUpperCase();
            if (na<nb) return -1;
            if (na > nb) return 1;
            return 0;
        });
        this.setState({
            dialog: OverlayDialog.createSelectDialog(title, displayList,
                (selected)=> {
                    callback(selected.name);
                    self.setState({dialog:undefined});
                },
                ()=> {
                    self.setState({dialog: undefined})
                })
        });
    }
    showColorDialog(current,okFunction){
        let self=this;
        this.setState({
            dialog: (props)=>{
                return <ColorDialog
                    {...props}
                    value={current}
                    closeCallback={()=>self.setState({dialog:undefined})}
                    okCallback={(selected)=>okFunction(selected)}
                    showUnset={true}
                    />
            }
        });
    }
    updateWidgetState(values,opt_new){
        let nvalues=undefined;
        if (opt_new){
            nvalues=values;
            let newState={
                widget: nvalues,
                sizeCount: this.sizeCount + 1,
                parameters:WidgetFactory.getEditableWidgetParameters(nvalues)};
            newState.parameters.forEach((p)=>{
                p.setDefault(newState.widget);
            });
            this.setState(newState);
        }
        else {
            nvalues = assign({}, this.state.widget, values);
            this.setState({widget: nvalues, sizeCount: this.sizeCount + 1})
        }
    }


    selectWidget(){
        let self=this;
        let widgetList=WidgetFactory.getAvailableWidgets();
        this.showSelection("Select Widget",widgetList,(selected)=>{
            this.updateWidgetState({name:selected},true);}
        );
    }
    render () {
        let self=this;
        let Dialog=this.state.dialog;
        let hasCurrent=this.props.current.name !== undefined;
        let parameters=this.state.parameters;
        if (this.sizeCount !== this.state.sizeCount && this.props.updateDimensions){
            this.sizeCount=this.state.sizeCount;
            window.setTimeout(self.props.updateDimensions,100);
        }
        return (
            <React.Fragment>
            <div className="selectDialog editWidgetDialog">
                <h3 className="dialogTitle">{this.props.title||'Select Widget'}</h3>
                <div className="info"><span className="label">Panel:</span>{this.props.panel}</div>
                {hasCurrent?
                    <div className="info"><span className="label">Current:</span>{this.props.current.name}</div>
                    :
                    null}
                {(this.props.weight !== undefined)?
                        <Input className="weigth"
                               type="number"
                               label="Weight:"
                               onChange={(ev)=>this.updateWidgetState({weight:ev})}
                               value={this.state.widget.weight!==undefined?this.state.widget.weight:1}/>
                    :null}
                <InputReadOnly className="selectElement info"
                    label="New Widget:"
                    onClick={this.selectWidget}
                    value={this.state.widget.name||'-Select Widget-'}/>
                {parameters.map((param)=>{
                    let selectFunction=undefined;
                    let inputFunction=undefined;
                    let ValueInput=undefined;
                    let current=param.getValue(this.state.widget);
                    let addClass="";
                    if (param.type == WidgetParameter.TYPE.DISPLAY){
                        selectFunction=()=>{};
                        ValueInput=InputReadOnly;
                        addClass=" disabled";
                    }
                    if (param.type == WidgetParameter.TYPE.STRING ||
                        param.type == WidgetParameter.TYPE.NUMBER||
                        param.type == WidgetParameter.TYPE.ARRAY){
                        inputFunction=(ev)=>{
                            self.updateWidgetState(param.setValue({},ev))
                        };
                        ValueInput=Input;
                    }
                    if (param.type == WidgetParameter.TYPE.SELECT || param.type == WidgetParameter.TYPE.KEY){
                        let title="Select "+param.name;
                        selectFunction=()=>{
                            self.showSelection(title,param.getList(),(selected)=>{
                                self.updateWidgetState(param.setValue({},selected));
                            })
                        };
                        ValueInput=InputReadOnly;
                    }
                    if (param.type == WidgetParameter.TYPE.BOOLEAN){
                        inputFunction=(val)=>{
                            self.updateWidgetState(param.setValue({},val));
                        };
                        ValueInput=Checkbox;
                    }
                    if (param.type == WidgetParameter.TYPE.COLOR){
                        selectFunction=()=>{
                            self.showColorDialog(param.getValue(self.state.widget),
                                (selected)=>{self.updateWidgetState(param.setValue({},selected))}
                            )
                        };
                        ValueInput=ColorSelector;
                    }
                    if (!ValueInput) return;
                    return <ValueInput
                            className={"editWidgetParam "+param.name+addClass}
                            key={param.name.replace(/  */,'')}
                            label={param.displayName}
                            onClick={selectFunction}
                            onChange={inputFunction}
                            value={current}
                        />
                })}
                {(this.state.widget.name !== undefined)?
                    <div className="insertButtons">
                        {hasCurrent?<button name="before" onClick={()=>this.insert(true)}>Before</button>:null}
                        {hasCurrent?<button name="after" onClick={()=>this.insert(false)}>After</button>:null}
                        {(!hasCurrent)?<button name="after" onClick={()=>this.insert(false)}>Insert</button>:null}
                    </div>
                    :null}
                <div className="dialogButtons">
                    <button name="cancel" onClick={this.props.closeCallback}>Cancel</button>
                    {this.props.updateCallback?
                        <button name="ok" onClick={()=>{
                        this.props.closeCallback();
                        let changes=this.state.widget;
                        if (this.props.weight){
                            if (changes.weight !== undefined) changes.weight=parseFloat(changes.weight)
                        }
                        else{
                            changes.weight=undefined;
                        }
                        this.props.updateCallback(changes);
                    }}>Update</button>
                    :null}
                    {this.props.removeCallback?
                    <button name="remove" onClick={()=>{
                        this.props.closeCallback();
                        this.props.removeCallback();
                    }}>Remove</button>:null}
                <div className="clear"></div>
                </div>
            </div>
            {Dialog?
                <DialogContainer
                    className="nested"
                    content={Dialog}
                    onClick={()=>{this.setState({dialog:undefined})}}
                />:
                null}
            </React.Fragment>
        );
    }
}

EditWidgetDialog.propTypes={
    title: PropTypes.string,
    panel: PropTypes.string,
    current:PropTypes.any,
    weight: PropTypes.bool,
    insertCallback: PropTypes.func,
    updateCallback: PropTypes.func,
    removeCallback: PropTypes.func,
    closeCallback: PropTypes.func.isRequired
};

const filterObject=(data)=>{
    for (let k in data){
        if (data[k] === undefined) delete data[k];
    }
    return data;
};

/**
 *
 * @param widgetItem
 * @param pagename
 * @param panelname
 * @param opt_beginning: insert at the beginning
 * @param opt_weight: show weight input
 * @return {boolean}
 */
EditWidgetDialog.createDialog=(widgetItem,pagename,panelname,opt_beginning,opt_weight)=>{
    if (! LayoutHandler.isEditing()) return false;
    let index=opt_beginning?-1:1;
    if (widgetItem){
        index=widgetItem.index;
    }
    OverlayDialog.dialog((props)=> {
        return <EditWidgetDialog
            {...props}
            weight={opt_weight||false}
            title="Select Widget"
            panel={panelname}
            current={widgetItem?widgetItem:{}}
            weight={opt_weight}
            insertCallback={(selected,before)=>{
                if (! selected || ! selected.name) return;
                let addMode=LayoutHandler.ADD_MODES.noAdd;
                if (widgetItem){
                    addMode=before?LayoutHandler.ADD_MODES.beforeIndex:LayoutHandler.ADD_MODES.afterIndex;
                }
                else{
                    addMode=opt_beginning?LayoutHandler.ADD_MODES.beginning:LayoutHandler.ADD_MODES.end;
                }
                LayoutHandler.replaceItem(pagename,panelname,index,selected,addMode);
            }}
            removeCallback={widgetItem?()=>{
                LayoutHandler.replaceItem(pagename,panelname,index);
            }:undefined}
            updateCallback={widgetItem?(changes)=>{
                LayoutHandler.replaceItem(pagename,panelname,index,filterObject(changes));
            }:undefined}
            />
    });
    return true;
};

export default EditWidgetDialog;