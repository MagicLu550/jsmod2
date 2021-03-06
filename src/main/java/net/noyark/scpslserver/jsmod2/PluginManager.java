package net.noyark.scpslserver.jsmod2;


import net.noyark.scpslserver.jsmod2.command.Command;
import net.noyark.scpslserver.jsmod2.command.NativeCommand;
import net.noyark.scpslserver.jsmod2.event.Event;
import net.noyark.scpslserver.jsmod2.event.EventManager;
import net.noyark.scpslserver.jsmod2.event.Listener;
import net.noyark.scpslserver.jsmod2.ex.EventException;
import net.noyark.scpslserver.jsmod2.ex.NoSuchPluginNameException;
import net.noyark.scpslserver.jsmod2.ex.PluginException;
import net.noyark.scpslserver.jsmod2.plugin.PluginClassLoader;
import net.noyark.scpslserver.jsmod2.utils.MethodInvokeMapper;
import net.noyark.scpslserver.jsmod2.utils.Utils;

import java.lang.reflect.Method;
import java.util.*;


/**
 * the plugin manager
 *
 * @author magiclu550 #(code) jsmod2
 */

public class PluginManager {

    private Map<Listener, List<MethodInvokeMapper>> listenerMapper = new HashMap<>();

    private List<NativeCommand> commands = new ArrayList<>();

    private Server server;

    public PluginManager(Server server){
        this.server = server;
    }




    public PluginClassLoader getPluginClassLoader(){
        return PluginClassLoader.getClassLoader();
    }

    public Plugin getPlugin(String pluginName){
        List<Plugin> plugins = getPluginClassLoader().getPlugins();
        for(Plugin plugin:plugins){
            if(plugin.getPluginName().equals(pluginName)){
                return plugin;
            }
        }
        throw new NoSuchPluginNameException(pluginName);
    }


    public List<Plugin> getPlugins(){
        return getPluginClassLoader().getPlugins();
    }

    public void registerCommand(Command command){
        Plugin plugin = command.getPlugin();
        if(plugin.isEnabled()){
            commands.add(command);
            server.getCommandInfo().put(command.getCommandName(),command.getDescription());
        }else{
            throw new PluginException("the plugin is not enabled");
        }
    }

    public void plugins(){
        List<Plugin> plugins = getPlugins();
        int i = 0;
        Utils.getMessageSender().info("plugins["+plugins.size()+"]:");
        for(Plugin plugin:plugins){
            Utils.getMessageSender().normal(plugin.getPluginName(),":"+plugin.getVersion());
            if(i%5==0){
                Utils.getMessageSender().newLine();
            }
            i++;
        }
    }

    /**
     * registerEvents
     * @param listener
     */

    public void registerEvents(final Listener listener,Plugin plugin){
        if(!plugin.isEnabled()){
            throw new PluginException("the plugin is not enabled");
        }
        Utils.TryCatch(()->{
            Class<?> type = listener.getClass();
            Listener listenerInstance = (Listener)type.newInstance();
            Method[] methods = type.getDeclaredMethods();
            for(Method method:methods) {
                addMethod(method,listenerInstance);
            }
        });
    }

    public void registerEvent(final Method method,Plugin plugin){
        if(!plugin.isEnabled()){
            throw new PluginException("the plugin is not enabled");
        }
        Utils.TryCatch(()->{
            Class<?> type = method.getDeclaringClass();
            addMethod(method,(Listener)(type.newInstance()));
        });
    }

    private void addMethod(Method method,Listener listenerInstance){
        EventManager manager = method.getDeclaredAnnotation(EventManager.class);
        if (manager != null) {
            MethodInvokeMapper mapper = new MethodInvokeMapper(manager.priority(), method);
            List<MethodInvokeMapper> methodMappers = listenerMapper.get(listenerInstance);
            if(methodMappers!=null){
                methodMappers.add(mapper);
                listenerMapper.put(listenerInstance,methodMappers);
            }else{
                methodMappers = new ArrayList<>();
                methodMappers.add(mapper);
                listenerMapper.put(listenerInstance,methodMappers);
            }
        }
    }

    public void callEvent(final Event event){
        Utils.TryCatch(()->{
            Set<Map.Entry<Listener,List<MethodInvokeMapper>>> set = listenerMapper.entrySet();
            for(Map.Entry<Listener,List<MethodInvokeMapper>> entry:set){
                Listener listener = entry.getKey();
                List<MethodInvokeMapper> methods = entry.getValue();
                List<MethodInvokeMapper> invoker = new ArrayList<>();
                for(MethodInvokeMapper mapper:methods){
                    Class[] classes = mapper.getMethod().getParameterTypes();
                    if(classes.length == 1){
                        if(classes[0].getName().equals(event.getEventName())){
                            invoker.add(mapper);
                        }
                    }else{
                        throw new EventException("the event method must have one parameter");
                    }
                }
                invoker.sort(Comparator.comparing(MethodInvokeMapper::getPriority));
                for(MethodInvokeMapper method:invoker){
                    method.getMethod().invoke(listener,event);
                }
            }
        });
    }


    public List<NativeCommand> getCommands(){
        return commands;
    }

    public List<NativeCommand> getPluginCommands(){
        List<NativeCommand> pluginCommands = new ArrayList<>();
        for(NativeCommand cmd:commands){
            if(cmd instanceof Command){
                pluginCommands.add(cmd);
            }
        }
        return pluginCommands;
    }
    public void clear(){
        commands.removeAll(getPluginCommands());
        listenerMapper.clear();
        this.getPlugins().clear();
    }
}
